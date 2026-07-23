import { useId } from "react";

export type QueryMode = "auto" | "simple" | "deep";

interface ModeSelectorProps {
  value: QueryMode;
  onChange: (value: QueryMode) => void;
  disabled?: boolean;
}

const MODES: readonly { value: QueryMode; label: string }[] = [
  { value: "auto", label: "智能模式" },
  { value: "simple", label: "快速问答" },
  { value: "deep", label: "深度研究" },
];

export function ModeSelector({
  value,
  onChange,
  disabled = false,
}: ModeSelectorProps) {
  const name = useId();
  return (
    <fieldset className="mode-selector" disabled={disabled}>
      <legend className="sr-only">查询模式</legend>
      {MODES.map((mode) => (
        <label className="mode-option" key={mode.value}>
          <input
            checked={value === mode.value}
            name={name}
            onChange={() => onChange(mode.value)}
            type="radio"
            value={mode.value}
          />
          <span>{mode.label}</span>
        </label>
      ))}
    </fieldset>
  );
}
