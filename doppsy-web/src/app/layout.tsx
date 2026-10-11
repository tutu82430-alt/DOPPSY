import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'Doppsy — Zero-Wait Healthcare',
  description:
    'Book verified doctors, veterinarians, and dieticians instantly. Zero wait time, premium care.',
  keywords: ['healthcare', 'doctor booking', 'veterinary', 'ayurveda', 'Doppsy'],
  authors: [{ name: 'Doppsy Healthcare Pvt. Ltd.' }],
  openGraph: {
    title: 'Doppsy — Zero-Wait Healthcare',
    description: 'Book verified healthcare providers instantly.',
    type: 'website',
  },
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" className="dark">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="antialiased">{children}</body>
    </html>
  )
}
