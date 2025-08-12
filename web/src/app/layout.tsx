import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'News Agent',
  description: '주식 뉴스 수집 및 중요도 평가 서비스',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  )
}