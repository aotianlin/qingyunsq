/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        "surface-tint": "#00c19a",
        "tertiary-fixed": "#ffdbcc",
        "surface": "#ffffff",
        "surface-container": "#f1f5f9",
        "on-primary-fixed": "#002a22",
        "on-surface-variant": "#64748b",
        "on-surface": "#0f172a",
        "on-tertiary-fixed-variant": "#7c2e00",
        "surface-bright": "#ffffff",
        "on-secondary-container": "#00732a",
        "on-secondary-fixed-variant": "#00531c",
        "primary-fixed-dim": "#6ee7b7",
        "surface-variant": "#f8fafc",
        "on-tertiary-fixed": "#351000",
        "primary": "#00c19a",
        "primary-fixed": "#e6fcf5",
        "background": "#f8f9fa",
        "surface-dim": "#e2e8f0",
        "inverse-primary": "#6ee7b7",
        "error": "#ef4444",
        "on-error": "#ffffff",
        "outline-variant": "#e2e8f0",
        "tertiary-container": "#c64f00",
        "secondary-fixed": "#a7f3d0",
        "on-tertiary-container": "#fffbff",
        "surface-container-low": "#f8fafc",
        "outline": "#94a3b8",
        "on-background": "#0f172a",
        "on-tertiary": "#ffffff",
        "error-container": "#fee2e2",
        "surface-container-high": "#e2e8f0",
        "on-error-container": "#93000a",
        "on-secondary-fixed": "#002107",
        "primary-container": "#00c19a",
        "on-primary-container": "#fefcff",
        "on-primary": "#ffffff",
        "secondary": "#006e28",
        "tertiary": "#9e3d00",
        "surface-container-lowest": "#ffffff",
        "secondary-container": "#6ffb85",
        "secondary-fixed-dim": "#53e16f",
        "surface-container-highest": "#e3e2e7",
        "on-secondary": "#ffffff",
        "on-primary-fixed-variant": "#004493",
        "inverse-on-surface": "#f1f0f5",
        "inverse-surface": "#2f3034",
        "tertiary-fixed-dim": "#ffb595"
      },
      borderRadius: {
        "DEFAULT": "0.25rem",
        "lg": "0.5rem",
        "xl": "0.75rem",
        "full": "9999px",
        "apple": "24px"
      },
      spacing: {
        "container-max": "1200px",
        "unit": "8px",
        "margin-desktop": "64px",
        "margin-mobile": "20px",
        "gutter": "24px"
      },
      fontFamily: {
        "body-md": ["Inter"],
        "headline-md": ["Inter"],
        "label-sm": ["Inter"],
        "body-lg": ["Inter"],
        "label-md": ["Inter"],
        "headline-lg": ["Inter"],
        "display-lg": ["Inter"]
      },
      fontSize: {
        "body-md": ["16px", { "lineHeight": "24px", "letterSpacing": "0em", "fontWeight": "400" }],
        "headline-md": ["24px", { "lineHeight": "32px", "letterSpacing": "-0.01em", "fontWeight": "600" }],
        "label-sm": ["12px", { "lineHeight": "16px", "letterSpacing": "0.02em", "fontWeight": "600" }],
        "body-lg": ["19px", { "lineHeight": "28px", "letterSpacing": "0em", "fontWeight": "400" }],
        "label-md": ["14px", { "lineHeight": "20px", "letterSpacing": "0.01em", "fontWeight": "500" }],
        "headline-lg": ["32px", { "lineHeight": "40px", "letterSpacing": "-0.01em", "fontWeight": "600" }],
        "display-lg": ["48px", { "lineHeight": "56px", "letterSpacing": "-0.02em", "fontWeight": "700" }]
      }
    }
  },
  plugins: [],
}
