local bridge = __gng_script
__gng_script = nil
if peripheral.hasType("left", "modem") then rednet.open("left") end
local makePackage = dofile("/rom/modules/main/cc/require.lua").make

local function validate(value, active, depth, budget)
    budget[1] = budget[1] + 1
    if depth > 16 or budget[1] > 4096 then error("Script values exceed the table limit", 0) end
    local kind = type(value)
    if kind == "number" then
        if value ~= value or value == math.huge or value == -math.huge then error("Script numbers must be finite", 0) end
    elseif kind == "string" then
        if #value > 32768 then error("Script text exceeds 32768 characters", 0) end
        budget[1] = budget[1] + math.floor(#value / 32)
    elseif kind == "table" then
        if active[value] then error("Script tables cannot contain cycles", 0) end
        active[value] = true
        for key, entry in pairs(value) do
            if type(key) ~= "string" and type(key) ~= "number" then error("Script table keys must be text or numbers", 0) end
            validate(key, active, depth + 1, budget)
            validate(entry, active, depth + 1, budget)
        end
        active[value] = nil
    elseif kind ~= "nil" and kind ~= "boolean" then
        error("Unsupported script value: " .. kind, 0)
    end
end

local function clone(value)
    if type(value) ~= "table" then return value end
    local result = {}
    for key, entry in pairs(value) do result[key] = clone(entry) end
    return result
end

local function message(err)
    return tostring(err):gsub("^Lua:(%d+):", function(line)
        return "Lua:" .. math.max(1, tonumber(line) - 1) .. ":"
    end)
end

local function environment()
    local env = {}
    for _, key in ipairs({
        "assert", "error", "ipairs", "next", "pairs", "pcall", "select", "tonumber", "tostring", "type", "unpack", "xpcall",
        "rawequal", "rawget", "rawlen", "rawset", "getmetatable", "setmetatable", "math", "string", "table", "coroutine", "bit32",
        "os", "fs", "io", "textutils", "colors", "colours", "keys", "vector", "paintutils", "term", "window",
        "parallel", "peripheral", "redstone", "rs", "http", "utf8", "print", "write", "read", "sleep",
        "settings", "rednet", "gps", "disk", "help", "bit", "_HOST", "_VERSION"
    }) do
        env[key] = _G[key]
    end
    env._G = env
    env.gng = peripheral.wrap("back")
    env.acc = env.gng
    env.load = function(code, name, mode) return load(code, name, mode or "t", env) end
    env.loadstring = env.load
    env.debug = {
        getinfo = debug.getinfo, getmetatable = debug.getmetatable, traceback = debug.traceback,
        getregistry = function() return { _LOADED = env.package and env.package.loaded or {} } end
    }
    env.require, env.package = makePackage(env, "/")
    env.package.loaded._G = env
    env.package.loaded.debug = env.debug
    return env
end

local ports, defaults, output, published = {}, {}, {}, {}
local env, fn, main, handler
local handlers, execInputs, execOutputs = {}, {}, {}
local revision, persistent = 0, false

local function replace(target, source)
    for key in pairs(target) do target[key] = nil end
    for key, value in pairs(source) do target[key] = clone(value) end
end

local function equal(a, b)
    if type(a) ~= type(b) then return false end
    if type(a) ~= "table" then return a == b end
    for key, value in pairs(a) do if not equal(value, b[key]) then return false end end
    for key in pairs(b) do if a[key] == nil then return false end end
    return true
end

local function publish()
    validate(output, {}, 0, {0})
    local changed, removed = {}, {}
    for key, value in pairs(output) do if not equal(value, published[key]) then changed[key] = value end end
    for key in pairs(published) do if output[key] == nil then removed[#removed + 1] = key end end
    if next(changed) or #removed > 0 then
        bridge.outputs(revision, changed, removed)
        published = clone(output)
    end
end

local function prepare(retained)
    env, handlers, output = environment(), {}, clone(defaults)
    published, persistent = clone(retained or {}), false
    local function port(name, out, exec, remove)
        local line = 0
        for level = 2, 8 do
            local info = debug.getinfo(level, "Sl")
            if not info then break end
            if info.source == "@Lua" then line = info.currentline - 1 break end
        end
        bridge.port(name, out, exec, remove, line)
        local schema = exec and (out and execOutputs or execInputs) or (out and defaults or ports)
        if remove then schema[name] = nil else schema[name] = exec and true or schema[name] or "" end
        if out and not exec then
            if remove then output[name] = nil elseif output[name] == nil then output[name] = "" end
        end
        return name
    end
    local methods = {
        addExecIn = function(name) return port(name, false, true, false) end,
        addExecOut = function(name) return port(name, true, true, false) end,
        removeExecIn = function(name) return port(name, false, true, true) end,
        removeExecOut = function(name) return port(name, true, true, true) end,
        addInputPort = function(name) return port(name, false, false, false) end,
        addOutputPort = function(name) return port(name, true, false, false) end,
        removeInputPort = function(name) return port(name, false, false, true) end,
        removeOutputPort = function(name) return port(name, true, false, true) end
    }
    env.node = setmetatable({}, {
        __index = function(_, key) if key == "persistentOutputs" then return persistent end return methods[key] end,
        __newindex = function(_, key, value)
            if key ~= "persistentOutputs" then error("Unknown node property: " .. tostring(key), 2) end
            if type(value) ~= "boolean" then error("node.persistentOutputs expects a boolean", 2) end
            if value and not persistent then
                for name, old in pairs(retained or {}) do
                    if defaults[name] ~= nil and equal(output[name], defaults[name]) then output[name] = clone(old) end
                end
            end
            persistent = value
            bridge.persistence(value)
        end
    })
    env.ports, env.output = ports, output
    env.execEvent = {
        on = function(name)
            if type(name) ~= "string" or not execInputs[name] then error("Unknown execution input: " .. tostring(name), 2) end
            if not handlers[name] then handlers[name] = {} end
            return handlers[name]
        end,
        pulse = function(name)
            if type(name) ~= "string" or not execOutputs[name] then error("Unknown execution output: " .. tostring(name), 2) end
            publish()
            bridge.pulse(name)
        end
    }
end

local function finish(task, err)
    local ok, value = pcall(publish)
    if not ok then err = value end
    bridge.complete(task.revision, err and message(err) or "", task.port)
end

local inputEvents = {mouse_click = true, mouse_drag = true, mouse_up = true, mouse_scroll = true, key = true, key_up = true, char = true, paste = true}

local function resume(task, event)
    while true do
        local ok, filter = coroutine.resume(task.thread, table.unpack(event or {}, 1, event and (event.n or #event) or 0))
        if not ok then finish(task, filter) return nil end
        if coroutine.status(task.thread) == "dead" then finish(task) return nil end
        task.filter = filter
        event = nil
        if task.inputs and (filter == nil or inputEvents[filter]) then
            for idx, pending in ipairs(task.inputs) do
                if filter == nil or pending[1] == filter then event = table.remove(task.inputs, idx) break end
            end
        end
        if not event then return task end
    end
end

local function dispatch(task, event)
    if not task or event[1] == "__gng_script" then return task end
    if task.filter == nil or task.filter == event[1] or event[1] == "terminate" then return resume(task, event) end
    if inputEvents[event[1]] and (task.filter == "task_complete" or task.filter == "timer") then
        task.inputs = task.inputs or {}
        if #task.inputs >= 128 then table.remove(task.inputs, 1) end
        task.inputs[#task.inputs + 1] = event
    end
    return task
end

while true do
    local req = bridge.next()
    if req then
        revision = req.revision
        if req.inputs then for key, value in pairs(req.inputs) do ports[key] = value end end
        if req.removed then for _, key in ipairs(req.removed) do ports[key] = nil end end
        if req.defaults then
            for key in pairs(defaults) do if req.defaults[key] == nil then output[key] = nil end end
            defaults = req.defaults
            for key, value in pairs(defaults) do if output[key] == nil then output[key] = clone(value) end end
        end
        if req.execInputs then execInputs = req.execInputs end
        if req.execOutputs then execOutputs = req.execOutputs end
        if req.source then
            main, handler, fn = nil, nil, nil
            local factory, err = load(req.source, "@Lua", "t", {})
            if factory then
                local ok, value = pcall(factory)
                if ok then fn = value else err = value end
            end
            bridge.validated(revision, fn and "" or message(err))
            if fn and req.autoStart then
                prepare(req.retained)
                main = resume({thread = coroutine.create(function() fn(ports, output, env) end), revision = revision, port = "startup"})
            end
        end
        if req.run and env and not handler then
            local callback = handlers[req.exec] and handlers[req.exec].run
            if type(callback) == "function" then
                handler = resume({thread = coroutine.create(function()
                    if not persistent then replace(output, defaults) end
                    callback()
                end), revision = revision, port = req.exec})
            else bridge.complete(revision, "No execution handler for '" .. req.exec .. "'", req.exec) end
        end
    end
    if env then
        local ok, err = pcall(publish)
        if not ok then
            bridge.complete(revision, message(err), "startup")
            main, handler = nil, nil
        end
    end
    bridge.ready(main ~= nil, handler ~= nil, env ~= nil)
    bridge.waiting(tostring(main and (main.filter or "*") or ""), tostring(handler and (handler.filter or "*") or ""))
    local event = table.pack(os.pullEventRaw())
    main = dispatch(main, event)
    handler = dispatch(handler, event)
end
