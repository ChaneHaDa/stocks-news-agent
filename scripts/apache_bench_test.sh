#!/bin/bash
# Apache Bench performance testing script
# Alternative testing method using ab (Apache Bench)

set -e

# Configuration
ML_URL="http://localhost:8001"
API_URL="http://localhost:8000"
CONCURRENT=10
REQUESTS=1000
TIMEOUT=5

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_header() {
    echo -e "${BLUE}$1${NC}"
    echo "=" $(printf "%*s" ${#1} "" | tr ' ' '=')
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function to test an endpoint with GET
test_get_endpoint() {
    local url=$1
    local name=$2
    
    print_header "Testing $name"
    echo "URL: $url"
    echo "Requests: $REQUESTS, Concurrency: $CONCURRENT"
    echo ""
    
    ab -n $REQUESTS -c $CONCURRENT -s $TIMEOUT "$url" 2>/dev/null | \
    awk '
        /Server Software:/ { print "Server: " $3 }
        /Complete requests:/ { print "Completed: " $3 }
        /Failed requests:/ { print "Failed: " $3 }
        /Requests per second:/ { print "RPS: " $4 " [" $5 "]" }
        /Time per request:.*\(mean\)/ { print "Mean time: " $4 "ms" }
        /Time per request:.*\(mean, across all concurrent requests\)/ { print "Mean time (concurrent): " $4 "ms" }
        /Transfer rate:/ { print "Transfer rate: " $3 " " $4 }
        /50%/ { p50 = $2 }
        /95%/ { p95 = $2; print "P50: " p50 "ms, P95: " p95 "ms" }
    '
    echo ""
}

# Function to test an endpoint with POST
test_post_endpoint() {
    local url=$1
    local name=$2
    local payload_file=$3
    
    print_header "Testing $name"
    echo "URL: $url"
    echo "Requests: $REQUESTS, Concurrency: $CONCURRENT"
    echo "Payload: $payload_file"
    echo ""
    
    ab -n $REQUESTS -c $CONCURRENT -s $TIMEOUT -T application/json -p "$payload_file" "$url" 2>/dev/null | \
    awk '
        /Server Software:/ { print "Server: " $3 }
        /Complete requests:/ { print "Completed: " $3 }
        /Failed requests:/ { print "Failed: " $3 }
        /Requests per second:/ { print "RPS: " $4 " [" $5 "]" }
        /Time per request:.*\(mean\)/ { print "Mean time: " $4 "ms" }
        /Time per request:.*\(mean, across all concurrent requests\)/ { print "Mean time (concurrent): " $4 "ms" }
        /Transfer rate:/ { print "Transfer rate: " $3 " " $4 }
        /50%/ { p50 = $2 }
        /95%/ { p95 = $2; print "P50: " p50 "ms, P95: " p95 "ms" }
    '
    echo ""
}

# Function to check if service is running
check_service() {
    local url=$1
    local name=$2
    
    if curl -s "$url" > /dev/null 2>&1; then
        print_success "$name is running"
        return 0
    else
        print_error "$name is not responding at $url"
        return 1
    fi
}

# Main execution
main() {
    print_header "ML Pipeline Performance Testing with Apache Bench"
    
    # Check if ab is installed
    if ! command -v ab &> /dev/null; then
        print_error "Apache Bench (ab) is not installed. Install with: sudo apt-get install apache2-utils"
        exit 1
    fi
    
    # Check if services are running
    check_service "$ML_URL/health" "ML Service" || exit 1
    check_service "$API_URL/healthz" "API Service" || exit 1
    
    # Create temporary payload files
    TEMP_DIR=$(mktemp -d)
    
    # Importance payload
    cat > "$TEMP_DIR/importance.json" << 'EOF'
{
    "title": "삼성전자 3분기 실적 발표",
    "content": "삼성전자가 3분기 매출 76조원을 기록했다고 발표했습니다. 메모리 반도체 부문의 실적 개선이 주효했습니다.",
    "source": "연합뉴스",
    "published_at": "2025-01-15T09:00:00Z"
}
EOF
    
    # Summarize payload
    cat > "$TEMP_DIR/summarize.json" << 'EOF'
{
    "title": "LG에너지솔루션 북미 공장 증설",
    "content": "LG에너지솔루션이 북미 지역에 새로운 배터리 공장을 증설한다고 발표했습니다. 총 5조원을 투자해 전기차 배터리 생산능력을 확대할 예정입니다. 이번 투자로 연간 100GWh 규모의 배터리를 생산할 수 있을 것으로 예상됩니다.",
    "source": "매일경제",
    "published_at": "2025-01-15T10:30:00Z",
    "tickers": ["373220"]
}
EOF
    
    # Run tests
    print_header "ML Service Performance Tests"
    
    # ML Health check
    test_get_endpoint "$ML_URL/health" "ML Health Check"
    
    # ML Importance scoring
    test_post_endpoint "$ML_URL/importance" "ML Importance Scoring" "$TEMP_DIR/importance.json"
    
    # ML Summarization (lower load)
    REQUESTS=200
    test_post_endpoint "$ML_URL/summarize" "ML Summarization" "$TEMP_DIR/summarize.json"
    REQUESTS=1000  # Reset
    
    print_header "API Service Performance Tests"
    
    # API News endpoint
    test_get_endpoint "$API_URL/news/top?n=20" "API News Endpoint"
    
    # API Admin status
    test_get_endpoint "$API_URL/admin/status" "API Admin Status"
    
    # Cleanup
    rm -rf "$TEMP_DIR"
    
    print_header "Performance Test Complete"
    print_success "Check results above for P95 latency < 300ms target"
    echo ""
    echo "📝 Performance Targets:"
    echo "   • RPS: ≥ 50 requests/second"
    echo "   • P95 Latency: < 300ms"
    echo "   • Success Rate: > 95%"
}

# Run main function
main "$@"