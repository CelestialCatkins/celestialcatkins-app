-- UI 插件：每次重绘整段重新执行，所以每次都能读到 calc 服务的当下值。
local ctx = ...

local shown = ctx.svc("calc", "display")
if type(shown) ~= "string" or shown == "" then
  shown = "0"
end

local err = ctx.svc("calc", "error")
if type(err) ~= "string" then
  err = ""
end

local children = {
  { type = "eyebrow", text = "CALC / 计算器" },
  { type = "metric", label = "当前结果", value = shown, unit = "CALC" },
}

if err ~= "" then
  children[#children + 1] = { type = "text", text = "错误：" .. err, muted = false }
end

children[#children + 1] = { type = "space", h = 8 }

local function keyRow(keys)
  local row = { type = "row", children = {} }
  for _, k in ipairs(keys) do
    row.children[#row.children + 1] = {
      type = k.style,
      text = k.text,
      emit = k.emit,
    }
  end
  children[#children + 1] = row
end

keyRow({
  { text = "C", style = "ghost", emit = "calc.press.clear" },
  { text = "±", style = "ghost", emit = "calc.press.neg" },
  { text = "%", style = "ghost", emit = "calc.press.pct" },
  { text = "÷", style = "button", emit = "calc.press.div" },
})

keyRow({
  { text = "7", style = "button", emit = "calc.press.7" },
  { text = "8", style = "button", emit = "calc.press.8" },
  { text = "9", style = "button", emit = "calc.press.9" },
  { text = "×", style = "button", emit = "calc.press.mul" },
})

keyRow({
  { text = "4", style = "button", emit = "calc.press.4" },
  { text = "5", style = "button", emit = "calc.press.5" },
  { text = "6", style = "button", emit = "calc.press.6" },
  { text = "−", style = "button", emit = "calc.press.sub" },
})

keyRow({
  { text = "1", style = "button", emit = "calc.press.1" },
  { text = "2", style = "button", emit = "calc.press.2" },
  { text = "3", style = "button", emit = "calc.press.3" },
  { text = "+", style = "button", emit = "calc.press.add" },
})

keyRow({
  { text = "0", style = "button", emit = "calc.press.0" },
  { text = ".", style = "button", emit = "calc.press.dot" },
  { text = "=", style = "button", emit = "calc.press.eq" },
  { text = "⌫", style = "ghost", emit = "calc.press.del" },
})

children[#children + 1] = { type = "divider" }
children[#children + 1] = { type = "kv", label = "依赖服务", value = "calc ← calc.logic" }
children[#children + 1] = {
  type = "note",
  text = "按键只发意图（calc.press.*），由 calc.logic 执行并写回状态。",
}

return {
  title = "计算器",
  icon = "grid",
  sub = "意图事件 → 逻辑层 → 服务状态",
  root = {
    type = "column",
    gap = 10,
    children = children,
  },
}
