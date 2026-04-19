import { cn } from '@/lib/utils';
import { ChevronDown } from 'lucide-react';

interface Option {
  Id: number;
  Name: string;
}

interface FilterDropdownProps {
  options: Option[];
  value: number | undefined;
  onChange: (id: number | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
}

export function FilterDropdown({
  options,
  value,
  onChange,
  placeholder = 'Select option...',
  disabled = false,
}: FilterDropdownProps) {
  return (
    <div className="relative min-w-[220px]">
      <select
        value={value ?? ''}
        onChange={(e) => {
          const val = e.target.value;
          onChange(val ? Number(val) : undefined);
        }}
        disabled={disabled}
        className="w-full appearance-none bg-white/5 border border-white/10 hover:border-white/20 hover:bg-white/10 transition-colors rounded-lg px-4 py-2 pr-10 text-sm text-white/90 outline-none focus:ring-2 focus:ring-violet-500/50 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <option value="" className="bg-[#0f172a] text-white/50">
          {placeholder}
        </option>
        {options.map((opt) => (
          <option key={opt.Id} value={opt.Id} className="bg-[#0f172a] text-white">
            {opt.Name}
          </option>
        ))}
      </select>
      <div className="absolute inset-y-0 right-0 flex items-center px-3 pointer-events-none text-white/40">
        <ChevronDown size={16} />
      </div>
    </div>
  );
}
