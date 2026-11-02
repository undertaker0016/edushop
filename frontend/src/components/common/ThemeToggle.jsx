import { Moon, Sun } from "lucide-react";
import { useTheme } from "../../contexts/ThemeContext";
export default function ThemeToggle() {
  const { theme, toggle } = useTheme();
  const label =
    theme === "dark"
      ? "Chuyển sang giao diện sáng"
      : "Chuyển sang giao diện tối";
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      aria-pressed={theme === "dark"}
      onClick={toggle}
      className="theme-toggle"
    >
      {theme === "dark" ? <Sun size={17} /> : <Moon size={17} />}
    </button>
  );
}
