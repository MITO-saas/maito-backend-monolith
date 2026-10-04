/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: 'var(--color-primary, #D97706)',
          dark: '#B45309',
          light: '#F59E0B',
          surface: '#FFFBEB'
        }
      },
      fontFamily: {
        heading: ['var(--font-heading, "Cabinet Grotesk")', 'Outfit', 'sans-serif'],
        body: ['var(--font-body, "Inter")', 'sans-serif']
      }
    },
  },
  plugins: [],
};
