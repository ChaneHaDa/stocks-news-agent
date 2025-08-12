'use client'

import { useEffect, useState } from 'react'
import axios from 'axios'

interface NewsItem {
  id: string
  source: string
  title: string
  url: string
  published_at: string
  tickers: string[]
  summary: string
  importance: number
  reason: {
    source_weight: number
    tickers_hit: number
    keywords_hit: number
    freshness: number
  }
}

interface NewsResponse {
  items: NewsItem[]
  next_cursor: string | null
}

export default function Home() {
  const [news, setNews] = useState<NewsItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchNews = async () => {
      try {
        const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000'
        const response = await axios.get<NewsResponse>(`${apiUrl}/news/top?n=10`)
        setNews(response.data.items)
        setError(null)
      } catch (err) {
        setError('서버 연결 실패')
        console.error('Error fetching news:', err)
      } finally {
        setLoading(false)
      }
    }

    fetchNews()
  }, [])

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-lg">뉴스를 불러오는 중...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          {error}
        </div>
      </div>
    )
  }

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  const getTopReasons = (reason: NewsItem['reason']) => {
    const reasons = [
      { key: 'source_weight', label: '신뢰성', value: reason.source_weight },
      { key: 'tickers_hit', label: '종목', value: reason.tickers_hit },
      { key: 'keywords_hit', label: '키워드', value: reason.keywords_hit },
      { key: 'freshness', label: '신속성', value: reason.freshness }
    ]
    
    return reasons
      .sort((a, b) => b.value - a.value)
      .slice(0, 3)
      .map(r => r.label)
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-4xl mx-auto px-4">
        <h1 className="text-3xl font-bold text-gray-900 mb-8">주요 뉴스</h1>
        
        <div className="space-y-6">
          {news.map((item) => (
            <div key={item.id} className="bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition-shadow">
              <div className="flex justify-between items-start mb-3">
                <div className="flex items-center space-x-2 text-sm text-gray-500">
                  <span className="font-medium">{item.source}</span>
                  <span>•</span>
                  <span>{formatDate(item.published_at)}</span>
                </div>
                <div className="bg-blue-100 text-blue-800 px-2 py-1 rounded text-xs font-semibold">
                  {Math.round(item.importance * 100)}점
                </div>
              </div>
              
              <h2 className="text-xl font-semibold mb-3 text-gray-900">
                <a href={item.url} target="_blank" rel="noopener noreferrer" 
                   className="hover:text-blue-600 transition-colors">
                  {item.title}
                </a>
              </h2>
              
              <p className="text-gray-600 mb-4 leading-relaxed">
                {item.summary}
              </p>
              
              <div className="flex justify-between items-center">
                <div className="flex space-x-2">
                  {item.tickers.map((ticker) => (
                    <span key={ticker} className="bg-green-100 text-green-800 px-2 py-1 rounded text-xs font-medium">
                      {ticker}
                    </span>
                  ))}
                </div>
                
                <div className="flex space-x-2">
                  {getTopReasons(item.reason).map((reason) => (
                    <span key={reason} className="bg-gray-100 text-gray-700 px-2 py-1 rounded text-xs">
                      {reason}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}