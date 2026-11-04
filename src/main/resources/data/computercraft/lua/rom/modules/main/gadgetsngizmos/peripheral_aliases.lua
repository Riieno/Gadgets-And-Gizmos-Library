local M = {}

-- Add an alias list without including helper functions in the lookup table's entries
function M.withList(aliases)
    assert(type(aliases) == "table", "peripheral aliases must be a table")
    return setmetatable(aliases, {
        __index = {
            list = function()
                local ids = {}
                for id, name in pairs(aliases) do
                    if type(id) == "string" and type(name) == "string" then
                        ids[#ids + 1] = id
                    end
                end
                table.sort(ids)
                return ids
            end,
        },
    })
end

return M
