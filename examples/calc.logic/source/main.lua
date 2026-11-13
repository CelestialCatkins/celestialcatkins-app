-- 逻辑插件：计算器状态机。只做业务，不碰界面。
-- 所有读取都先做类型兜底，避免把 nil 当成 table/string 去索引。
local ctx = ...

local MAX_INPUT = 16

local function str(v, fallback)
  if type(v) == "string" then return v end
  return fallback
end

local function num(v, fallback)
  local n = tonumber(v)
  if n == nil then return fallback end
  return n
end

local function display()
  return str(ctx.state.get("cur", "0"), "0")
end

local function errorText()
  return str(ctx.state.get("err", ""), "")
end

local function fmt(n)
  if type(n) ~= "number" then return "0" end
  if n ~= n then return "错误" end
  if n == math.huge or n == -math.huge then return "错误" end
  local s = string.format("%.10g", n)
  -- LuaJ 的数字都是 double，%g 会把 15 打成 "15.0"；去掉多余的 .0。
  if s:sub(-2) == ".0" then s = s:sub(1, -3) end
  if s == "-0" then s = "0" end
  return s
end

local function reset()
  ctx.state.set("cur", "0")
  ctx.state.set("acc", 0)
  ctx.state.set("op", "")
  ctx.state.set("err", "")
end

-- 只支持 + - * /；除以 0 返回 nil，由调用方转成可读错误。
local function apply(a, op, b)
  if op == "+" then return a + b end
  if op == "-" then return a - b end
  if op == "*" then return a * b end
  if op == "/" then
    if b == 0 then return nil end
    return a / b
  end
  return b
end

local function press(key)
  key = str(key, "")
  local cur = display()
  local op = str(ctx.state.get("op", ""), "")
  local acc = num(ctx.state.get("acc", 0), 0)
  ctx.state.set("err", "")

  if key == "clear" then
    reset()
  elseif key == "del" then
    if #cur <= 1 or (cur:sub(1, 1) == "-" and #cur == 2) then
      cur = "0"
    else
      cur = cur:sub(1, -2)
    end
    ctx.state.set("cur", cur)
  elseif key == "neg" then
    if cur:sub(1, 1) == "-" then
      cur = cur:sub(2)
    elseif cur ~= "0" then
      cur = "-" .. cur
    end
    ctx.state.set("cur", cur)
  elseif key == "pct" then
    ctx.state.set("cur", fmt(num(cur, 0) / 100))
  elseif key == "." then
    if not string.find(cur, ".", 1, true) then
      ctx.state.set("cur", cur .. ".")
    end
  elseif key == "=" then
    local b = num(cur, 0)
    if op == "" then
      ctx.state.set("cur", fmt(b))
      ctx.state.set("acc", b)
    else
      local r = apply(acc, op, b)
      if r == nil then
        ctx.state.set("err", "不能除以 0")
      else
        ctx.state.set("cur", fmt(r))
        ctx.state.set("acc", r)
        ctx.state.set("op", "")
      end
    end
  elseif key == "+" or key == "-" or key == "*" or key == "/" then
    local b = num(cur, 0)
    if op == "" then
      acc = b
    else
      local r = apply(acc, op, b)
      if r == nil then
        ctx.state.set("err", "不能除以 0")
        ctx.emit("calc.changed", { value = display() })
        return display()
      end
      acc = r
    end
    ctx.state.set("acc", acc)
    ctx.state.set("op", key)
    ctx.state.set("cur", "0")
  elseif string.match(key, "^%d$") ~= nil then
    if cur == "0" then
      cur = key
    elseif #cur < MAX_INPUT then
      cur = cur .. key
    end
    ctx.state.set("cur", cur)
  end

  ctx.emit("calc.changed", { value = display() })
  return display()
end

ctx.provide("calc", {
  display = function()
    return display()
  end,
  error = function()
    return errorText()
  end,
  press = function(args)
    local key = ""
    if type(args) == "table" then
      key = str(args.key, "")
    end
    return press(key)
  end,
  clear = function()
    reset()
    ctx.emit("calc.changed", { value = "0" })
    return "0"
  end,
})

-- 事件名 → 按键。UI 的 button 只能 emit 事件（没有 payload），
-- 所以每个键一个事件，这里是唯一的映射处。
local INTENTS = {
  ["calc.press.0"] = "0",
  ["calc.press.1"] = "1",
  ["calc.press.2"] = "2",
  ["calc.press.3"] = "3",
  ["calc.press.4"] = "4",
  ["calc.press.5"] = "5",
  ["calc.press.6"] = "6",
  ["calc.press.7"] = "7",
  ["calc.press.8"] = "8",
  ["calc.press.9"] = "9",
  ["calc.press.dot"] = ".",
  ["calc.press.add"] = "+",
  ["calc.press.sub"] = "-",
  ["calc.press.mul"] = "*",
  ["calc.press.div"] = "/",
  ["calc.press.eq"] = "=",
  ["calc.press.clear"] = "clear",
  ["calc.press.del"] = "del",
  ["calc.press.pct"] = "pct",
  ["calc.press.neg"] = "neg",
}

local function bind(event, key)
  ctx.on(event, function()
    ctx.svc("calc", "press", { key = key })
  end)
end

for event, key in pairs(INTENTS) do
  bind(event, key)
end
