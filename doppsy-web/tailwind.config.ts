import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['var(--font-inter)', 'system-ui', 'sans-serif'],
      },
      colors: {
        // Doppsy brand palette
        brand: {
          50:  '#ecfeff',
          100: '#cffafe',
          200: '#a5f3fc',
          300: '#67e8f9',
          400: '#22d3ee',
          500: '#06b6d4',   // primary cyan
          600: '#0891b2',
          700: '#0e7490',
          800: '#155e75',
          900: '#164e63',
        },
        surface: {
          DEFAULT: '#0d1117',
          card:    '#161b27',
          border:  'rgba(255,255,255,0.08)',
        },
      },
      backgroundImage: {
        'brand-gradient': 'linear-gradient(135deg, #06b6d4 0%, #8b5cf6 100%)',
        'dark-gradient':  'linear-gradient(180deg, #0a0f1e 0%, #0d1526 100%)',
        'card-gradient':  'linear-gradient(135deg, rgba(6,182,212,0.06) 0%, rgba(139,92,246,0.06) 100%)',
      },
      animation: {
        'fade-in':     'fadeIn 0.5s ease-in-out',
        'slide-up':    'slideUp 0.4s ease-out',
        'pulse-glow':  'pulseGlow 2s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%':   { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%':   { opacity: '0', transform: 'translateY(20px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        pulseGlow: {
          '0%, 100%': { boxShadow: '0 0 20px rgba(6,182,212,0.3)' },
          '50%':      { boxShadow: '0 0 40px rgba(6,182,212,0.6)' },
        },
      },
      backdropBlur: {
        xs: '2px',
      },
    },
  },
  plugins: [],
}

export default config
