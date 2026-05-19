package com.astrixforge.devicemasker.xposed.hooker

import android.content.SharedPreferences
import com.astrixforge.devicemasker.xposed.DualLog
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.WeakHashMap

@Suppress("TooManyFunctions")
internal object ProcMapsHooker : BaseSpoofHooker("ProcMapsHooker") {
    private const val MAX_SKIP_LINES = 64
    private const val MAX_MAPS_BYTES = 2 * 1024 * 1024
    private const val UNSIGNED_BYTE_MASK = 0xff

    private val hiddenPatterns =
        listOf(
            "libxposed",
            "liblspd",
            "lsposed",
            "lspd",
            "riru",
            "zygisk",
            "sandhook",
            "substrate",
            "edxposed",
            "devicemasker",
            "com.astrixforge.devicemasker",
        )

    private val mapsReaders = Collections.synchronizedMap(WeakHashMap<Any, Unit>())
    private val streamPaths = Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val streamBuffers = Collections.synchronizedMap(WeakHashMap<Any, ByteReadState>())
    private val bypassHook = ThreadLocal<Boolean>()

    fun hook(cl: ClassLoader, xi: XposedInterface, prefs: SharedPreferences, pkg: String) {
        val policy = ProcMapsPolicy.fromPrefs(prefs, pkg)
        if (policy.javaLineRedactionEnabled) {
            hookReaderConstructors(cl, xi)
            hookBufferedReaderReadLine(cl, xi)
            hookRandomAccessFileReadLine(cl, xi)
        }
        if (policy.javaByteRedactionEnabled) {
            hookFileInputStreamConstructors(cl, xi)
            hookFileInputStreamReads(cl, xi)
        }
        if (policy.javaNioRedactionEnabled) {
            hookNioFiles(cl, xi)
        }
    }

    internal fun isSensitiveMapsPath(
        path: String,
        currentPid: Int = android.os.Process.myPid(),
    ): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized == "/proc/self/maps" ||
            normalized == "/proc/$currentPid/maps" ||
            normalized == "/proc/self/smaps" ||
            normalized == "/proc/$currentPid/smaps"
    }

    internal fun shouldRedactMapsLine(line: String): Boolean =
        hiddenPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) }

    internal fun filterMapsText(text: String): String =
        text
            .removeSuffix("\n")
            .lineSequence()
            .filterNot(::shouldRedactMapsLine)
            .joinToString(separator = "\n")
            .let { filtered -> if (text.endsWith('\n')) "$filtered\n" else filtered }

    internal fun pathFromArg(arg: Any?): String? =
        when (arg) {
            is String -> arg.replace('\\', '/')
            is File -> arg.path.replace('\\', '/')
            else -> null
        }

    internal fun markReader(reader: Any) {
        mapsReaders[reader] = Unit
    }

    internal fun isMarkedReader(reader: Any?): Boolean =
        reader != null && mapsReaders.containsKey(reader)

    private fun markStream(stream: Any, path: String) {
        streamPaths[stream] = path
    }

    private fun hookReaderConstructors(cl: ClassLoader, xi: XposedInterface) {
        val fileReaderClass = cl.loadClassOrNull("java.io.FileReader") ?: return
        listOf(String::class.java, File::class.java).forEach { parameter ->
            safeHook("FileReader($parameter)") {
                fileReaderClass.getDeclaredConstructor(parameter).also { constructor ->
                    constructor.isAccessible = true
                    xi.hook(constructor)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                val path = pathFromArg(chain.args.firstOrNull())
                                if (path != null && isSensitiveMapsPath(path)) {
                                    chain.thisObject?.let(::markReader)
                                }
                                result
                            }
                        )
                    xi.deoptimize(constructor)
                }
            }
        }

        val bufferedReaderClass = cl.loadClassOrNull("java.io.BufferedReader") ?: return
        safeHook("BufferedReader(Reader)") {
            val readerClass = cl.loadClass("java.io.Reader")
            bufferedReaderClass.getDeclaredConstructor(readerClass).also { constructor ->
                constructor.isAccessible = true
                xi.hook(constructor)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            if (isMarkedReader(chain.args.firstOrNull())) {
                                chain.thisObject?.let(::markReader)
                            }
                            result
                        }
                    )
                xi.deoptimize(constructor)
            }
        }
    }

    private fun hookBufferedReaderReadLine(cl: ClassLoader, xi: XposedInterface) {
        val bufferedReaderClass = cl.loadClassOrNull("java.io.BufferedReader") ?: return
        safeHook("BufferedReader.readLine") {
            bufferedReaderClass.methodOrNull("readLine")?.let { method ->
                xi.hook(method)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            if (!isMarkedReader(chain.thisObject)) return@stableHooker result
                            nextVisibleLine(chain, result as? String)
                        }
                    )
                xi.deoptimize(method)
            }
        }
    }

    private fun hookRandomAccessFileReadLine(cl: ClassLoader, xi: XposedInterface) {
        val rafClass = cl.loadClassOrNull("java.io.RandomAccessFile") ?: return
        listOf(String::class.java, File::class.java).forEach { parameter ->
            safeHook("RandomAccessFile($parameter, String)") {
                rafClass.getDeclaredConstructor(parameter, String::class.java).also { constructor ->
                    constructor.isAccessible = true
                    xi.hook(constructor)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                val path = pathFromArg(chain.args.firstOrNull())
                                if (path != null && isSensitiveMapsPath(path)) {
                                    chain.thisObject?.let(::markReader)
                                }
                                result
                            }
                        )
                    xi.deoptimize(constructor)
                }
            }
        }
        safeHook("RandomAccessFile.readLine") {
            rafClass.methodOrNull("readLine")?.let { method ->
                xi.hook(method)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            if (!isMarkedReader(chain.thisObject)) return@stableHooker result
                            nextVisibleLine(chain, result as? String)
                        }
                    )
                xi.deoptimize(method)
            }
        }
    }

    private fun nextVisibleLine(chain: XposedInterface.Chain, firstLine: String?): Any? {
        if (firstLine == null || !shouldRedactMapsLine(firstLine)) return firstLine
        var skipped = 0
        while (skipped < MAX_SKIP_LINES) {
            val candidate =
                try {
                    chain.proceed() as? String ?: return null
                } catch (_: Throwable) {
                    return firstLine
                }
            if (!shouldRedactMapsLine(candidate)) return candidate
            skipped++
        }
        return firstLine
    }

    private fun hookFileInputStreamConstructors(cl: ClassLoader, xi: XposedInterface) {
        val streamClass = cl.loadClassOrNull("java.io.FileInputStream") ?: return
        listOf(String::class.java, File::class.java).forEach { parameter ->
            safeHook("FileInputStream($parameter)") {
                streamClass.getDeclaredConstructor(parameter).also { constructor ->
                    constructor.isAccessible = true
                    xi.hook(constructor)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                val path = pathFromArg(chain.args.firstOrNull())
                                if (path != null && isSensitiveMapsPath(path)) {
                                    chain.thisObject?.let { markStream(it, path) }
                                }
                                result
                            }
                        )
                    xi.deoptimize(constructor)
                }
            }
        }
    }

    @Suppress("CognitiveComplexMethod")
    private fun hookFileInputStreamReads(cl: ClassLoader, xi: XposedInterface) {
        val streamClass = cl.loadClassOrNull("java.io.FileInputStream") ?: return
        safeHook("FileInputStream.read()") {
            streamClass.methodOrNull("read")?.let { method ->
                xi.hook(method)
                    .intercept(
                        stableHooker { chain ->
                            if (bypassHook.get() == true) return@stableHooker chain.proceed()
                            val state =
                                byteStateOrNull(chain.thisObject)
                                    ?: return@stableHooker chain.proceed()
                            state.readOne()
                        }
                    )
                xi.deoptimize(method)
            }
        }
        safeHook("FileInputStream.read(byteArray)") {
            streamClass.methodOrNull("read", ByteArray::class.java)?.let { method ->
                xi.hook(method)
                    .intercept(
                        stableHooker { chain ->
                            if (bypassHook.get() == true) return@stableHooker chain.proceed()
                            val target = chain.args.firstOrNull() as? ByteArray
                            val state = byteStateOrNull(chain.thisObject)
                            if (target == null || state == null) return@stableHooker chain.proceed()
                            state.readInto(target, offset = 0, length = target.size)
                        }
                    )
                xi.deoptimize(method)
            }
        }
        safeHook("FileInputStream.read(byteArray, int, int)") {
            streamClass
                .methodOrNull(
                    "read",
                    ByteArray::class.java,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                )
                ?.let { method ->
                    xi.hook(method)
                        .intercept(
                            stableHooker { chain ->
                                if (bypassHook.get() == true) return@stableHooker chain.proceed()
                                val target = chain.args.getOrNull(0) as? ByteArray
                                val offset = chain.args.getOrNull(1) as? Int
                                val length = chain.args.getOrNull(2) as? Int
                                val state = byteStateOrNull(chain.thisObject)
                                if (
                                    target == null ||
                                        offset == null ||
                                        length == null ||
                                        state == null
                                ) {
                                    return@stableHooker chain.proceed()
                                }
                                state.readInto(target, offset, length)
                            }
                        )
                    xi.deoptimize(method)
                }
        }
    }

    private fun byteStateOrNull(stream: Any?): ByteReadState? {
        if (stream == null) return null
        streamBuffers[stream]?.let {
            return it
        }
        val path = streamPaths[stream] ?: return null
        val bytes = readSanitizedBytes(path) ?: return null
        return ByteReadState(bytes).also { streamBuffers[stream] = it }
    }

    private fun readSanitizedBytes(path: String): ByteArray? {
        bypassHook.set(true)
        return try {
            val file = File(path)
            if (!file.isFile || file.length() > MAX_MAPS_BYTES) return null
            val raw = file.readBytes()
            val text = decodeMapsBytes(raw)
            filterMapsText(text).toByteArray(StandardCharsets.UTF_8)
        } catch (e: SecurityException) {
            DualLog.warn(tag, "Proc maps byte redaction skipped: ${e.javaClass.simpleName}", e)
            null
        } catch (e: java.io.IOException) {
            DualLog.warn(tag, "Proc maps byte redaction skipped: ${e.javaClass.simpleName}", e)
            null
        } finally {
            bypassHook.set(false)
        }
    }

    private fun decodeMapsBytes(bytes: ByteArray): String =
        runCatching { bytes.toString(StandardCharsets.UTF_8) }
            .getOrElse { bytes.toString(Charset.forName("ISO-8859-1")) }

    @Suppress("CognitiveComplexMethod")
    private fun hookNioFiles(cl: ClassLoader, xi: XposedInterface) {
        val filesClass = cl.loadClassOrNull("java.nio.file.Files") ?: return
        val pathClass = cl.loadClassOrNull("java.nio.file.Path") ?: return
        val charsetClass = Charset::class.java

        safeHook("Files.readAllLines(Path, Charset)") {
            filesClass.methodOrNull("readAllLines", pathClass, charsetClass)?.let { method ->
                xi.hook(method)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val path = chain.args.firstOrNull()?.toString()
                            if (path == null || !isSensitiveMapsPath(path))
                                return@stableHooker result
                            @Suppress("UNCHECKED_CAST")
                            val lines = result as? List<String> ?: return@stableHooker result
                            ArrayList(lines.filterNot(::shouldRedactMapsLine))
                        }
                    )
                xi.deoptimize(method)
            }
        }
        safeHook("Files.readString(Path)") {
            filesClass.declaredMethods
                .filter { it.name == "readString" && it.parameterTypes.firstOrNull() == pathClass }
                .forEach { method ->
                    method.isAccessible = true
                    xi.hook(method)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                val path = chain.args.firstOrNull()?.toString()
                                if (path == null || !isSensitiveMapsPath(path)) {
                                    return@stableHooker result
                                }
                                (result as? String)?.let(::filterMapsText) ?: result
                            }
                        )
                    xi.deoptimize(method)
                }
        }
        safeHook("Files.newBufferedReader(Path, Charset)") {
            filesClass.methodOrNull("newBufferedReader", pathClass, charsetClass)?.let { method ->
                xi.hook(method)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val path = chain.args.firstOrNull()?.toString()
                            if (path != null && isSensitiveMapsPath(path) && result != null) {
                                markReader(result)
                            }
                            result
                        }
                    )
                xi.deoptimize(method)
            }
        }
    }

    private class ByteReadState(private val bytes: ByteArray) {
        private var position = 0

        fun readOne(): Int {
            if (position >= bytes.size) return -1
            return bytes[position++].toInt() and UNSIGNED_BYTE_MASK
        }

        fun readInto(target: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position >= bytes.size) return -1
            val count = minOf(length, bytes.size - position)
            bytes.copyInto(
                target,
                destinationOffset = offset,
                startIndex = position,
                endIndex = position + count,
            )
            position += count
            return count
        }
    }
}
