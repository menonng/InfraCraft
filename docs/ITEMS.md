# 아이템 조합법·획득처

InfraCraft 의 모든 아이템은 **조합하거나, 또는 다른 곳에서 얻어요.** 조합할 수 있는 아이템은 조합대 그림을 함께 실었어요.
조합법이 없는 아이템은 구조물 상자나 교역, 설계도로 얻어요. 게임 안에서는 레시피 책(또는 JEI·EMI)으로도 볼 수 있어요.

| 얻는 법 | 아이템 |
|---|---|
| 조합 (35) | 포석길, 벽돌 포장길, 판석길, 아스팔트, 차선 아스팔트, 이정석, 가로등, 이정표, 도로 포장기, 고속 선로, 특급 선로, 블레이즈 선로, 엔더 선로 핵, 역, 분기기, 승차권, 선로 부설기, 측량기, 터널 굴착기, 실드 굴진기, 터널 라이닝, 터널 조명, 승강장 안전선, 승강장 안전문, 역 승강기, 지하철 건설 안내서, 지하 역사 설계도, 지상 역사 설계도, 전동차 도면, 객차 도면, 물살 블록, 보트 리프트, 마구간, 말 호루라기, 철 편자 |
| 획득 (4) | 항구 설계도, 교역소, 화물 상자, 다이아몬드 편자 |

> 그림은 `recipe-images` 워크플로가 게임 jar 의 텍스처로 그려요(`tools/recipes/render_recipes.py`). 이 문서는 `python3 tools/recipes/items_doc.py` 로 다시 만들어요.

## 도로

### <img src="recipes/icons/setts.png" width="32" height="32" align="top"> 포석길
<sub>Cobbled Setts · `infracraft:setts`</sub>

**조합** → 8개

![포석길 조합법](recipes/setts.png)

재료: 윤나는 화강암 4 + 조약돌 4

### <img src="recipes/icons/brick_paving.png" width="32" height="32" align="top"> 벽돌 포장길
<sub>Brick Paving · `infracraft:brick_paving`</sub>

**조합** → 8개

![벽돌 포장길 조합법](recipes/brick_paving.png)

재료: 벽돌 4 + 모래 4

### <img src="recipes/icons/flagstone.png" width="32" height="32" align="top"> 판석길
<sub>Flagstone Path · `infracraft:flagstone`</sub>

**조합** → 8개

![판석길 조합법](recipes/flagstone.png)

재료: 매끄러운 돌 4 + 이끼 블록 4

### <img src="recipes/icons/asphalt.png" width="32" height="32" align="top"> 아스팔트
<sub>Asphalt · `infracraft:asphalt`</sub>

**조합** → 8개

![아스팔트 조합법](recipes/asphalt.png)

재료: 자갈 4 + 모래 4 + 석탄 1

### <img src="recipes/icons/road_line.png" width="32" height="32" align="top"> 차선 아스팔트
<sub>Lane-Marked Asphalt · `infracraft:road_line`</sub>

**조합** → 4개

![차선 아스팔트 조합법](recipes/road_line.png)

재료: 흰색 염료 1 + 아스팔트 4

### <img src="recipes/icons/milestone.png" width="32" height="32" align="top"> 이정석
<sub>Milestone · `infracraft:milestone`</sub>

**조합** → 1개

![이정석 조합법](recipes/milestone.png)

재료: 석재 벽돌 2 + 나침반 1

### <img src="recipes/icons/street_lamp.png" width="32" height="32" align="top"> 가로등
<sub>Street Lamp · `infracraft:street_lamp`</sub>

**조합** → 1개

![가로등 조합법](recipes/street_lamp.png)

재료: 금 조각 2 + 랜턴 1 + 철창 2

### <img src="recipes/icons/signpost.png" width="32" height="32" align="top"> 이정표
<sub>Signpost · `infracraft:signpost`</sub>

**조합** → 2개

![이정표 조합법](recipes/signpost.png)

재료: 판자 6 + 막대기 1

### <img src="recipes/icons/road_paver.png" width="32" height="32" align="top"> 도로 포장기
<sub>Road Paver · `infracraft:road_paver`</sub>

**조합** → 1개

![도로 포장기 조합법](recipes/road_paver.png)

재료: 철 주괴 4 + 막대기 3

## 철도

### <img src="recipes/icons/high_speed_rail.png" width="32" height="32" align="top"> 고속 선로
<sub>High-Speed Rail · `infracraft:high_speed_rail`</sub>

**조합** → 6개

![고속 선로 조합법](recipes/high_speed_rail.png)

재료: 철 주괴 6 + 금 주괴 1 + 막대기 1 + 레드스톤 가루 1

### <img src="recipes/icons/express_rail.png" width="32" height="32" align="top"> 특급 선로
<sub>Express Rail · `infracraft:express_rail`</sub>

**조합** → 16개

![특급 선로 조합법](recipes/express_rail.png)

재료: 금 주괴 6 + 엔더 선로 핵 1 + 레드스톤 가루 1

### <img src="recipes/icons/blaze_rail.png" width="32" height="32" align="top"> 블레이즈 선로
<sub>Blaze Rail · `infracraft:blaze_rail`</sub>

**조합** → 6개

![블레이즈 선로 조합법](recipes/blaze_rail.png)

재료: 금 주괴 6 + 블레이즈 막대기 1 + 막대기 1 + 레드스톤 가루 1

### <img src="recipes/icons/ender_rail_core.png" width="32" height="32" align="top"> 엔더 선로 핵
<sub>Ender Rail Core · `infracraft:ender_rail_core`</sub>

**조합** → 1개

![엔더 선로 핵 조합법](recipes/ender_rail_core.png)

재료: 튀긴 후렴과 4 + 엔더의 눈 4 + 레드스톤 블록 1

### <img src="recipes/icons/station.png" width="32" height="32" align="top"> 역
<sub>Station · `infracraft:station`</sub>

**조합** → 1개

![역 조합법](recipes/station.png)

재료: 금 주괴 4 + 나침반 1 + 레드스톤 가루 1 + 매끄러운 돌 3

### <img src="recipes/icons/rail_switch.png" width="32" height="32" align="top"> 분기기
<sub>Rail Switch · `infracraft:rail_switch`</sub>

**조합** → 1개

![분기기 조합법](recipes/rail_switch.png)

재료: 레드스톤 가루 2 + 레드스톤 비교기 1 + 매끄러운 돌 3

### <img src="recipes/icons/ticket.png" width="32" height="32" align="top"> 승차권
<sub>Ticket · `infracraft:ticket`</sub>

**조합** → 8개

![승차권 조합법](recipes/ticket.png)

재료: 종이 8 + 금 조각 1

### <img src="recipes/icons/track_layer.png" width="32" height="32" align="top"> 선로 부설기
<sub>Track Layer · `infracraft:track_layer`</sub>

**조합** → 1개

![선로 부설기 조합법](recipes/track_layer.png)

재료: 레일 3 + 철 주괴 2 + 막대기 2

### <img src="recipes/icons/surveyor.png" width="32" height="32" align="top"> 측량기
<sub>Surveyor · `infracraft:surveyor`</sub>

**조합** → 1개

![측량기 조합법](recipes/surveyor.png)

재료: 자수정 조각 1 + 구리 주괴 2 + 막대기 2

## 지하철

### <img src="recipes/icons/tunnel_borer.png" width="32" height="32" align="top"> 터널 굴착기
<sub>Tunnel Borer · `infracraft:tunnel_borer`</sub>

**조합** → 1개

![터널 굴착기 조합법](recipes/tunnel_borer.png)

재료: 다이아몬드 2 + 철 블록 3 + 피스톤 1 + 막대기 1

### <img src="recipes/icons/subway_shield.png" width="32" height="32" align="top"> 실드 굴진기
<sub>Subway Shield Machine · `infracraft:subway_shield`</sub>

**조합** → 1개

![실드 굴진기 조합법](recipes/subway_shield.png)

재료: 철 블록 6 + 다이아몬드 2 + 터널 굴착기 1

### <img src="recipes/icons/tunnel_lining.png" width="32" height="32" align="top"> 터널 라이닝
<sub>Tunnel Lining · `infracraft:tunnel_lining`</sub>

**조합** → 16개

![터널 라이닝 조합법](recipes/tunnel_lining.png)

재료: 돌 종류 8 + 점토 덩이 1

### <img src="recipes/icons/tunnel_light.png" width="32" height="32" align="top"> 터널 조명
<sub>Tunnel Light · `infracraft:tunnel_light`</sub>

**조합** → 4개

![터널 조명 조합법](recipes/tunnel_light.png)

재료: 철 조각 4 + 유리판 4 + 발광석 가루 1

### <img src="recipes/icons/platform_edge.png" width="32" height="32" align="top"> 승강장 안전선
<sub>Platform Edge · `infracraft:platform_edge`</sub>

**조합** → 8개

![승강장 안전선 조합법](recipes/platform_edge.png)

재료: 터널 라이닝 8 + 노란색 염료 1

### <img src="recipes/icons/screen_door.png" width="32" height="32" align="top"> 승강장 안전문
<sub>Platform Screen Door · `infracraft:screen_door`</sub>

**조합** → 2개

![승강장 안전문 조합법](recipes/screen_door.png)

재료: 철 주괴 4 + 유리판 2

### <img src="recipes/icons/station_elevator.png" width="32" height="32" align="top"> 역 승강기
<sub>Station Elevator · `infracraft:station_elevator`</sub>

**조합** → 1개

![역 승강기 조합법](recipes/station_elevator.png)

재료: 철 주괴 6 + 피스톤 2 + 레드스톤 블록 1

### 지하철 건설 안내서
<sub>완성된 책(바닐라 아이템)</sub>

**조합** → 1개

![지하철 건설 안내서 조합법](recipes/subway_guide.png)

재료: 책 1 + 레일 1

## 역사 설계도

### <img src="recipes/icons/station_blueprint.png" width="32" height="32" align="top"> 지하 역사 설계도
<sub>Underground Station Blueprint · `infracraft:station_blueprint`</sub>

**조합** → 1개

![지하 역사 설계도 조합법](recipes/station_blueprint.png)

재료: 파란색 염료 4 + 종이 4 + 역 1

### <img src="recipes/icons/ground_station_blueprint.png" width="32" height="32" align="top"> 지상 역사 설계도
<sub>Surface Station Blueprint · `infracraft:ground_station_blueprint`</sub>

**조합** → 1개

![지상 역사 설계도 조합법](recipes/ground_station_blueprint.png)

재료: 파란색 염료 4 + 랜턴 1 + 종이 2 + 역 1 + 가문비나무 반 블록 1

### <img src="recipes/icons/harbor_blueprint.png" width="32" height="32" align="top"> 항구 설계도
<sub>Harbor Blueprint · `infracraft:harbor_blueprint`</sub>

**획득**

- 난파선 지도 상자: 35%
- 난파선 보급품 상자: 15%
- 큰 해저 유적 상자: 20%
- 마을 어부 집 상자: 20%

## 차량 도면 (Create)

### <img src="recipes/icons/metro_car_blueprint.png" width="32" height="32" align="top"> 전동차 도면
<sub>Metro Car Plan · `infracraft:metro_car_blueprint`</sub>

**조합** → 1개 · *Create 가 있을 때만*

![전동차 도면 조합법](recipes/metro_car_blueprint.png)

재료: 하늘색 염료 4 + 종이 4 + 기차 케이스(Create) 1

### <img src="recipes/icons/coach_blueprint.png" width="32" height="32" align="top"> 객차 도면
<sub>Passenger Coach Plan · `infracraft:coach_blueprint`</sub>

**조합** → 1개 · *Create 가 있을 때만*

![객차 도면 조합법](recipes/coach_blueprint.png)

재료: 빨간색 염료 4 + 종이 3 + 기차 케이스(Create) 1 + 가문비나무 판자 1

## 수운·항구

### <img src="recipes/icons/water_current.png" width="32" height="32" align="top"> 물살 블록
<sub>Water Current · `infracraft:water_current`</sub>

**조합** → 4개

![물살 블록 조합법](recipes/water_current.png)

재료: 프리즈머린 6 + 켈프 2 + 레드스톤 가루 1

### <img src="recipes/icons/boat_lift.png" width="32" height="32" align="top"> 보트 리프트
<sub>Boat Lift · `infracraft:boat_lift`</sub>

**조합** → 1개

![보트 리프트 조합법](recipes/boat_lift.png)

재료: 프리즈머린 벽돌 6 + 영혼 모래 2 + 레드스톤 가루 1

### <img src="recipes/icons/trading_post.png" width="32" height="32" align="top"> 교역소
<sub>Trading Post · `infracraft:trading_post`</sub>

**획득**

- **항구 설계도**를 쓰면 잔교 입구에 설치돼요. 부수면 아이템으로 회수해 옮길 수 있어요.

### <img src="recipes/icons/cargo_crate.png" width="32" height="32" align="top"> 화물 상자
<sub>Cargo Crate · `infracraft:cargo_crate`</sub>

**획득**

- **교역소**에 그 항구의 특산품을 들고 쓰면 받아요(최대 64개를 담아요).

## 말

### <img src="recipes/icons/stable.png" width="32" height="32" align="top"> 마구간
<sub>Stable · `infracraft:stable`</sub>

**조합** → 1개

![마구간 조합법](recipes/stable.png)

재료: 판자 4 + 건초 더미 4

### <img src="recipes/icons/whistle.png" width="32" height="32" align="top"> 말 호루라기
<sub>Horse Whistle · `infracraft:whistle`</sub>

**조합** → 1개

![말 호루라기 조합법](recipes/whistle.png)

재료: 금 주괴 1 + 실 1

### <img src="recipes/icons/iron_horseshoe.png" width="32" height="32" align="top"> 철 편자
<sub>Iron Horseshoe · `infracraft:iron_horseshoe`</sub>

**조합** → 2개

![철 편자 조합법](recipes/iron_horseshoe.png)

재료: 철 주괴 7

### <img src="recipes/icons/diamond_horseshoe.png" width="32" height="32" align="top"> 다이아몬드 편자
<sub>Diamond Horseshoe · `infracraft:diamond_horseshoe`</sub>

**획득**

- 마을 무기 대장장이 상자: 12%
- 마을 도구 대장장이 상자: 10%
- 사막 피라미드 상자: 8%

## 히든

<details><summary>스포일러: 독수리자리의 별을 모으는 극악의 조합법(펼치기)</summary>

창작 탭과 레시피 책에는 나오지 않아요. 모든 재료는 서바이벌에서 구할 수 있지만, 위더·엔더 드래곤·고대 도시·시련의 회당·고고학·바다 깊은 곳을 모두 거쳐야 해요.

### <img src="recipes/icons/star_altair.png" width="32" height="32" align="top"> 별조각: 알타이르

![별조각: 알타이르 조합법](recipes/star_altair.png)

재료: 다이아몬드 블록 4 + 엔드 수정 4 + 신호기 1

### <img src="recipes/icons/star_tarazed.png" width="32" height="32" align="top"> 별조각: 타라제드

![별조각: 타라제드 조합법](recipes/star_tarazed.png)

재료: 화염구 4 + 블레이즈 막대기 4 + 네더라이트 블록 1

### <img src="recipes/icons/star_alshain.png" width="32" height="32" align="top"> 별조각: 알샤인

![별조각: 알샤인 조합법](recipes/star_alshain.png)

재료: 금 블록 8 + 불사의 토템 1

### <img src="recipes/icons/star_deneb_el_okab.png" width="32" height="32" align="top"> 별조각: 덴드라

![별조각: 덴드라 조합법](recipes/star_deneb_el_okab.png)

재료: 셜커 껍데기 4 + 엔더 선로 핵 4 + 드래곤 머리 1

### <img src="recipes/icons/star_bezek.png" width="32" height="32" align="top"> 별조각: 베제크

![별조각: 베제크 조합법](recipes/star_bezek.png)

재료: 스컬크 촉매 4 + 자수정 군집 4 + 회생 나침반 1

### <img src="recipes/icons/star_chesus.png" width="32" height="32" align="top"> 별조각: 체수스

![별조각: 체수스 조합법](recipes/star_chesus.png)

재료: 브리즈 막대기 4 + 불길한 시련 열쇠 4 + 무거운 코어 1

### <img src="recipes/icons/star_libertas.png" width="32" height="32" align="top"> 별조각: 리베르타스

![별조각: 리베르타스 조합법](recipes/star_libertas.png)

재료: 앵무조개 껍데기 4 + 프리즈머린 수정 2 + 스니퍼 알 2 + 바다의 심장 1

### <img src="recipes/icons/star_phonix.png" width="32" height="32" align="top"> 별조각: 포닉스

![별조각: 포닉스 조합법](recipes/star_phonix.png)

재료: 가스트 눈물 4 + 화염구 4 + 마법이 부여된 황금 사과 1

### <img src="recipes/icons/star_petra.png" width="32" height="32" align="top"> 별조각: 페트라

![별조각: 페트라 조합법](recipes/star_petra.png)

재료: 네더라이트 주괴 4 + 우는 흑요석 4 + 자석석 1

### <img src="recipes/icons/aquila_core.png" width="32" height="32" align="top"> 아퀼라 성좌핵

![아퀼라 성좌핵 조합법](recipes/aquila_core.png)

재료: 별조각: 덴드라 1 + 별조각: 타라제드 1 + 별조각: 체수스 1 + 별조각: 페트라 1 + 별조각: 알타이르 1 + 별조각: 베제크 1 + 별조각: 포닉스 1 + 별조각: 알샤인 1 + 별조각: 리베르타스 1

### <img src="recipes/icons/astra_obliterator.png" width="32" height="32" align="top"> 아스트라 오브리터레이터

![아스트라 오브리터레이터 조합법](recipes/astra_obliterator.png)

재료: 네더의 별 3 + 아퀼라 성좌핵 1 + 네더라이트 검 1

</details>
