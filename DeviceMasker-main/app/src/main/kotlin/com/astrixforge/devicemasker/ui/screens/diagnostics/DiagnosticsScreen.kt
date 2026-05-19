package com.astrixforge.devicemasker.ui.screens.diagnostics

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.models.SpoofCategory
import com.astrixforge.devicemasker.data.models.SpoofType
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
import com.astrixforge.devicemasker.diagnostics.DiagnosticStatus
import com.astrixforge.devicemasker.ui.components.expressive.AnimatedSectionStateful
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressivePullToRefresh
import com.astrixforge.devicemasker.ui.components.expressive.StatusIndicatorWithIcon
import com.astrixforge.devicemasker.ui.navigation.diagnosticsViewModelFactory
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import com.astrixforge.devicemasker.ui.theme.statusActive
import com.astrixforge.devicemasker.ui.theme.statusWarning
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Screen for diagnosing spoofing effectiveness.
 *
 * Features:
 * - Shows current detected values
 * - Compares with spoofed values
 * - Anti-detection test results
 * - Refresh functionality
 *
 * Uses MVVM architecture with ViewModel for state management.
 *
 * @param viewModel The DiagnosticsViewModel for state management
 * @param onNavigateBack Callback to navigate back
 * @param modifier Optional modifier
 */
@Composable
fun DiagnosticsScreen(
    application: Application,
    repository: ISpoofRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel =
        viewModel(
            factory =
                remember(application, repository) {
                    diagnosticsViewModelFactory(application, repository)
                }
        ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DiagnosticsContent(
        isXposedActive = state.isXposedActive,
        diagnosticResults = state.diagnosticResults,
        antiDetectionResults = state.antiDetectionResults,
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/** Stateless content for DiagnosticsScreen. */
@Composable
fun DiagnosticsContent(
    isXposedActive: Boolean,
    diagnosticResults: ImmutableList<DiagnosticResult>,
    antiDetectionResults: ImmutableList<AntiDetectionTest>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use the reusable ExpressivePullToRefresh component
    ExpressivePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with back button - refresh is now pull-to-refresh
            item(contentType = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.group_spoofing_back),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.diagnostics_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(id = R.string.diagnostics_pull_refresh),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Module Status Card
            item(key = "module_status", contentType = "module_status") {
                ModuleStatusCard(isXposedActive = isXposedActive)
            }

            // Config Sync Info Card
            item(key = "config_sync_info", contentType = "config_sync_info") {
                ConfigSyncInfoCard()
            }

            // Anti-Detection Section
            item(key = "anti_detection", contentType = "anti_detection") {
                AntiDetectionSection(tests = antiDetectionResults)
            }

            // Spoofing Results by Category
            SpoofCategory.entries.forEach { category ->
                val categoryResults =
                    diagnosticResults.filter { it.type.category == category }.toImmutableList()
                if (categoryResults.isNotEmpty()) {
                    item(key = "category_${category.name}", contentType = "category") {
                        CategoryDiagnosticSection(category = category, results = categoryResults)
                    }
                }
            }
        }
    }
}

/** Info card explaining config sync behavior and restart requirements. */
@Composable
private fun ConfigSyncInfoCard() {
    ExpressiveCard(
        onClick = { /* Info card */ },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.diagnostics_config_sync_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.diagnostics_config_sync_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * Section showing anti-detection test results. Uses AnimatedSection for spring-based
 * expand/collapse animation.
 */
@Composable
private fun AntiDetectionSection(tests: ImmutableList<AntiDetectionTest>) {
    val passedCount = tests.count { it.isPassed }

    AnimatedSectionStateful(
        title = stringResource(id = R.string.diagnostics_anti_detection),
        icon = Icons.Outlined.Security,
        count =
            pluralStringResource(
                id = R.plurals.diagnostics_tests_passed,
                count = passedCount,
                passedCount,
                tests.size,
            ),
        countColor =
            if (passedCount == tests.size) {
                MaterialTheme.colorScheme.statusActive
            } else {
                MaterialTheme.colorScheme.statusWarning
            },
    ) {
        tests.forEach { test ->
            AntiDetectionTestItem(test = test)
            if (test != tests.last()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** Individual anti-detection test result item. */
@Composable
private fun AntiDetectionTestItem(test: AntiDetectionTest) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusIndicatorWithIcon(isSuccess = test.isPassed, size = 24.dp)

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = stringResource(id = test.nameRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(id = test.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Section showing diagnostic results for a category. Uses AnimatedSection for spring-based
 * expand/collapse animation.
 */
@Composable
private fun CategoryDiagnosticSection(
    category: SpoofCategory,
    results: ImmutableList<DiagnosticResult>,
) {
    AnimatedSectionStateful(
        title = category.displayName,
        count =
            pluralStringResource(
                id = R.plurals.diagnostics_items_count,
                count = results.size,
                results.size,
            ),
    ) {
        results.forEach { result ->
            DiagnosticResultItem(result = result)
            if (result != results.last()) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** Individual diagnostic result item. */
@Composable
private fun DiagnosticResultItem(result: DiagnosticResult) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.type.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            StatusBadge(status = result.status)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Value comparison
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValueColumn(
                labelRes = R.string.diagnostics_real_label,
                value = result.realValue,
                modifier = Modifier.weight(1f),
            )
            ValueColumn(
                labelRes = R.string.diagnostics_spoofed_label,
                value = result.spoofedValue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Status badge for diagnostic result. */
@Composable
private fun StatusBadge(status: DiagnosticStatus) {
    val (color, textRes) =
        when (status) {
            DiagnosticStatus.SUCCESS ->
                MaterialTheme.colorScheme.statusActive to R.string.diagnostics_hook_success
            DiagnosticStatus.WARNING ->
                MaterialTheme.colorScheme.statusWarning to R.string.diagnostics_hook_failure
            DiagnosticStatus.INACTIVE ->
                MaterialTheme.colorScheme.onSurfaceVariant to R.string.diagnostics_hook_inactive
        }

    Box(
        modifier =
            Modifier.background(
                    color = color.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(id = textRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** Column showing a value with label. */
@Composable
private fun ValueColumn(labelRes: Int, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value ?: stringResource(id = R.string.diagnostics_unknown),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color =
                if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun DiagnosticsContentPreview() {
    DeviceMaskerTheme {
        DiagnosticsContent(
            isXposedActive = true,
            diagnosticResults =
                persistentListOf(
                    DiagnosticResult(
                        type = SpoofType.ANDROID_ID,
                        realValue = "a1b2c3d4e5f6g7h8",
                        spoofedValue = "x1y2z3w4v5u6t7s8",
                        isActive = true,
                        isSpoofed = true,
                    ),
                    DiagnosticResult(
                        type = SpoofType.DEVICE_PROFILE,
                        realValue = "Google Pixel 9",
                        spoofedValue = "Samsung Galaxy S24",
                        isActive = true,
                        isSpoofed = true,
                    ),
                ),
            antiDetectionResults =
                persistentListOf(
                    AntiDetectionTest(
                        R.string.diagnostics_test_stack_trace,
                        R.string.diagnostics_test_stack_trace_desc,
                        true,
                    ),
                    AntiDetectionTest(
                        R.string.diagnostics_test_class_loading,
                        R.string.diagnostics_test_class_loading_desc,
                        true,
                    ),
                    AntiDetectionTest(
                        R.string.diagnostics_test_native_hiding,
                        R.string.diagnostics_test_native_hiding_desc,
                        false,
                    ),
                ),
            isRefreshing = false,
            onRefresh = {},
            onNavigateBack = {},
        )
    }
}
