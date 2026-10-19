(() => {
  "use strict";
  const copy = (value) => value == null ? value : JSON.parse(JSON.stringify(value));
  const owns = (obj, key) => Object.prototype.hasOwnProperty.call(obj || {}, key);
  const numeric = (type) => ["number", "integer", "int", "float", "double"].includes(type);
  const typeOf = (value) => Array.isArray(value) ? "list" : value === null || value === undefined ? "any"
    : typeof value === "object" ? "map" : typeof value;

  function decode(entry) {
    if (!entry || !entry.Payload) return undefined;
    const payload = entry.Payload;
    if (entry.Type === "list" || entry.Type === "map") {
      const fields = owns(payload, "Value") && payload.Value && typeof payload.Value === "object" && !payload.Value.Type
        ? payload.Value : payload;
      const order = entry.EntryOrder || Object.keys(fields);
      const keys = [...new Set([...order, ...Object.keys(fields)])].filter((key) => owns(fields, key));
      return entry.Type === "list" ? keys.sort((a, b) => Number(a) - Number(b)).map((key) => decode(fields[key]) ?? copy(fields[key]))
        : Object.fromEntries(keys.map((key) => [key, decode(fields[key]) ?? copy(fields[key])]));
    }
    if (owns(payload, "Value")) return entry.Type === "boolean" ? Boolean(payload.Value) : copy(payload.Value);
    return copy(payload);
  }

  function value(data, port, fallback) {
    return owns(data, port) ? data[port] : decode(data?.Defaults?.[port]) ?? fallback;
  }

  function defaults(def, supplied = {}) {
    const data = { ...copy(def?.defaultData || {}), ...copy(supplied) };
    for (const key of ["Defaults", "InputOptions", "InputLabels", "OutputLabels"]) {
      data[key] = { ...copy(def?.defaultData?.[key] || {}), ...copy(supplied[key] || {}) };
    }
    for (const [key, entry] of Object.entries(data.Defaults)) {
      if (!owns(data, key)) data[key] = decode(entry);
    }
    return data;
  }

  function ordered(ports, preferred) {
    return Object.fromEntries([...new Set([...(preferred || []), ...Object.keys(ports)])]
      .filter((key) => owns(ports, key)).map((key) => [key, ports[key]]));
  }

  function accepts(data, rules, field, selector) {
    const rule = rules?.[field];
    if (!rule) return true;
    const selected = String(value(data, selector || rule.Selector, rule.Fallback) ?? "").toLowerCase();
    return (rule.Values || []).some((entry) => String(entry).toLowerCase() === selected);
  }

  function visibleInputs(ports, data) {
    const conditions = data.TargetInputConditions;
    if (!conditions) return ports;
    const bindings = data.DataTargetBindings || {};
    const targetPorts = {};
    for (const [port, entries] of Object.entries(bindings)) {
      for (const binding of entries) (targetPorts[binding.Target] ||= {})[binding.Port] = port;
    }
    return Object.fromEntries(Object.entries(ports).filter(([port]) => {
      const entries = bindings[port] || [];
      if (!entries.length) return accepts(data, conditions[""], port);
      return entries.some((binding) => {
        const rules = conditions[binding.Target];
        const selector = rules?.[binding.Port]?.Selector;
        return accepts(data, rules, binding.Port, targetPorts[binding.Target]?.[selector] || selector);
      });
    }));
  }

  function ports(def, data = {}, direction, visible = false) {
    const dynamicKey = direction === "inputs" ? "DynamicInputs" : "DynamicOutputs";
    let base = { ...(def?.[direction] || {}) };
    const variants = def?.variants || [];
    const controlled = new Set();
    for (const variant of variants) Object.keys(variant[direction] || {}).forEach((key) => controlled.add(key));
    const selected = variants.filter((variant) => String(value(data, variant.selector,
      decode(def?.defaultData?.Defaults?.[variant.selector]))) === variant.value);
    for (const variant of selected) base = { ...variant[direction] };
    let result = { ...base, ...(data[dynamicKey] || {}) };
    if (selected.length) for (const key of controlled) if (!owns(base, key)) delete result[key];
    if ((data.DynamicConstructor || data.TextInputsConfigured) && direction === "inputs") result = { ...(data.DynamicInputs || {}) };
    if (data.SwitchType === "data") {
      result = direction === "outputs" ? { value: "any" } : { selector: "number", default: data.SwitchCaseTypes?.default || "any",
        ...Object.fromEntries(Object.entries(data.DynamicInputs || {}).filter(([key]) => /^case_\d+$/.test(key))
          .map(([key, type]) => [key, data.SwitchCaseTypes?.[key] || type])) };
    }
    if (direction === "inputs") {
      for (const [port, source] of Object.entries(def?.matchingInputs || {})) {
        result[port] = data.DynamicInputs?.[source] || typeOf(value(data, source));
      }
      if (data.PreventIntegralWindup !== undefined && owns(def?.inputs, "p")) {
        if (data.PreventIntegralWindup) { result.integral_min = "number"; result.integral_max = "number"; }
        else { delete result.integral_min; delete result.integral_max; }
      }
      result = visibleInputs(result, data);
    }
    const orderKey = direction === "inputs" ? "InputOrder" : "OutputOrder";
    result = ordered(result, data[orderKey] || def?.defaultData?.[orderKey]);
    const collapse = data[direction === "inputs" ? "CollapseInputsToMap" : "CollapseOutputsToMap"];
    const mapPort = direction === "inputs" ? "input_map" : "output_map";
    if (collapse && Object.values(result).some((type) => type !== "exec")) {
      result[mapPort] = "map";
      if (visible) result = Object.fromEntries(Object.entries(result).filter(([key, type]) => type === "exec" || key === mapPort));
    }
    return result;
  }

  function collection(operation, input, item, key, index) {
    const list = Array.isArray(input);
    const result = copy(list ? input : input && typeof input === "object" ? input : []);
    const keys = Object.keys(result);
    const values = Object.values(result);
    const equals = (a, b) => {
      if (a === b) return true;
      if (!a || !b || typeof a !== "object" || typeof b !== "object" || Array.isArray(a) !== Array.isArray(b)) return false;
      const keys = Object.keys(a);
      return keys.length === Object.keys(b).length && keys.every((key) => owns(b, key) && equals(a[key], b[key]));
    };
    const found = values.findIndex((value) => equals(value, item));
    if (operation === "contains") return { result: found >= 0 };
    if (operation === "index_of") return { index: found };
    if (operation === "push") {
      if (Array.isArray(result)) result.push(copy(item)); else result[String(key ?? "key")] = copy(item);
      return { result };
    }
    const requested = Number(index);
    const position = Math.floor(requested);
    if (!Number.isFinite(requested) || requested < 0 || position >= keys.length) return { result: copy(input ?? []), value: null, removed: false };
    const removed = result[keys[position]];
    if (Array.isArray(result)) result.splice(position, 1); else delete result[keys[position]];
    return { result, value: removed, removed: true };
  }

  globalThis.GadgetsGraphNodes = Object.freeze({ copy, decode, defaults, value, ports, ordered, numeric, typeOf, collection,
    format: (value, precise) => typeof value === "number" ? precise ? String(value) : Number.isInteger(value) ? String(value) : value.toFixed(2) : String(value ?? "") });
})();
