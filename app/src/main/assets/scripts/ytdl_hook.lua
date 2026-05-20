local utils = require 'mp.utils'
local msg = require 'mp.msg'
local options = require 'mp.options'

local o = {
    ytdl_path = "python3",
}

options.read_options(o)

local function exec(args)
    -- Workaround for Android 10+ (API 29+) execution restrictions.
    -- We use /system/bin/sh -c to run the python interpreter from the lib folder.
    -- We also need to prepend the script path (yt-dlp.py) if the interpreter is used directly.

    local script_path = mp.command_native({"expand-path", "~~/ytdl/yt-dlp.py"})
    local cmd_args = {o.ytdl_path, script_path}
    for i = 2, #args do
        table.insert(cmd_args, args[i])
    end

    local cmd_str = ""
    for _, arg in ipairs(cmd_args) do
        cmd_str = cmd_str .. ' "' .. arg:gsub('"', '\\"') .. '"'
    end

    msg.debug("Executing: /system/bin/sh -c" .. cmd_str)

    return mp.command_native({
        name = "subprocess",
        args = {"/system/bin/sh", "-c", cmd_str},
        capture_stdout = true,
        capture_stderr = true,
    })
end

-- Hook into on_load to resolve URLs
mp.add_hook("on_load", 10, function()
    local url = mp.get_property("stream-open-filename", "")
    if not url:find("^https?://") and not url:find("^ytdl://") then
        return
    end

    if url:find("^ytdl://") == 1 then
        url = url:sub(8)
    end

    msg.info("Resolving URL via ytdl: " .. url)

    local format = mp.get_property("options/ytdl-format", "")
    local args = {o.ytdl_path, "--no-warnings", "-J", "--flat-playlist"}

    if format ~= "" then
        table.insert(args, "--format")
        table.insert(args, format)
    end

    table.insert(args, "--")
    table.insert(args, url)

    local res = exec(args)

    if res.status ~= 0 or not res.stdout or res.stdout == "" then
        msg.error("ytdl failed: " .. (res.stderr or "unknown error"))
        return
    end

    local json, err = utils.parse_json(res.stdout)
    if not json then
        msg.error("Failed to parse ytdl JSON: " .. (err or "unknown error"))
        return
    end

    if json.url then
        msg.info("Resolved URL: " .. json.url)
        mp.set_property("stream-open-filename", json.url)
        if json.title then
            mp.set_property("file-local-options/force-media-title", json.title)
        end
    elseif json._type == "playlist" or json._type == "multi_video" then
        -- Simplified playlist handling: just play the first entry
        if json.entries and #json.entries > 0 then
            local entry = json.entries[1]
            if entry.url then
                 mp.set_property("stream-open-filename", entry.url)
            end
        end
    end
end)
