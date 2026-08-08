package com.plocoli.yomp

import kotlinx.serialization.Serializable

/** 홈 레일 하나. title=화면에 보이는 제목, query=실제 Spotify 검색어(생략 시 title 사용). */
@Serializable
data class Fav(val title: String, val query: String = title)

/**
 * 홈 화면에 "레일"로 보여줄 부모 큐레이션 즐겨찾기.
 * 위에 있을수록 홈 위쪽 레일. 아이 취향에 맞게 자유롭게 추가/삭제/순서변경 하세요.
 *
 * 설계 메모:
 * - Spotify 는 신규 앱에 장르/추천 API 를 막아둠(403). 그래서 "장르"는 구체적인
 *   아티스트·영화 검색어로 표현한다. (장르식 검색은 스팸/연주 커버 등 품질 낮아 제외)
 * - 영어 이름이 흔한 단어와 겹치면(IVE↔"I've") 다른 언어 검색어를 query 로 쓴다.
 * - 일본어 노래는 일본어 원문을 query 로 넣어야 결과가 정확하다.
 */
object Favorites {
    val items: List<Fav> = listOf(
        // 케이팝 걸그룹 (아이가 좋아하는)
        Fav("BLACKPINK"),
        Fav("IVE", query = "아이브"),   // 'IVE' 검색은 "I've..." 곡이 잡혀서 한글명 사용
        Fav("(G)I-DLE"),
        Fav("aespa"),
        Fav("NewJeans"),
        Fav("KATSEYE"),
        Fav("HUNTR/X"),                 // 영화 'KPop Demon Hunters' 걸그룹 (Golden 등)
        Fav("EJAE"),                    // 'Golden' 부른 보컬 (In Another World 등)

        // 케이팝 걸그룹 (탐색 — 새로 발견용, 자유롭게 교체하세요)
        Fav("LE SSERAFIM"),
        Fav("TWICE"),
        Fav("ITZY"),
        Fav("BABYMONSTER"),

        // 재즈 / 기타 (부모 취향)
        Fav("Esperanza Spalding"),      // Black Gold 등

        // 여자 어린이용 만화/캐릭터 & 키즈 팝
        Fav("L.O.L. Surprise"),
        Fav("My Little Pony"),
        Fav("Kidz Bop"),

        // 디즈니 & 애니메이션 영화 사운드트랙
        Fav("KPop Demon Hunters"),      // 케데헌 OST
        Fav("Frozen", query = "Disney Frozen"),   // 'Frozen'만 검색하면 마돈나 곡 섞임 → Disney 명시
        Fav("Moana"),                   // 모아나
        Fav("Encanto"),                 // 엔칸토
        Fav("Descendants"),             // 디센던츠 (Dove Cameron 등)

        // 일본 동요·아니메 주제가 (일본어 학습용)
        Fav("이웃집 토토로", query = "となりのトトロ"),
        Fav("일본 동요", query = "ケロポンズ"),   // 케로퐁즈=일본 대표 키즈송 듀오(다양+깨끗). '童謡' 검색은 무서운 곡 섞여서 이걸로
        Fav("지브리 주제가", query = "ジブリ"),
        Fav("호빵맨 (アンパンマン)", query = "アンパンマン"),
    )
}
