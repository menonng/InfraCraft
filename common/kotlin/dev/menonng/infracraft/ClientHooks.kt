package dev.menonng.infracraft

/** 공통 코드에서 클라이언트 전용 동작을 부르기 위한 자리. 클라이언트 초기화가 채운다(전용 서버에서는 비어 있다). */
object ClientHooks {
    /** 아스트라 오브리터레이터를 왼손에 들고 우클릭: 조준 모드 켜기/끄기 */
    @Volatile var astraAim: (() -> Unit)? = null
}
