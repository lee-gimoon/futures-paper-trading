package com.example.futurespapertrading.paper;

import org.springframework.http.HttpStatus;                                       // 201/400/503 등 HTTP 상태코드 모음
import org.springframework.security.core.context.ReactiveSecurityContextHolder;   // 현재 로그인 정보를 꺼내는 통로
import org.springframework.web.bind.annotation.GetMapping;                        // HTTP GET 요청을 이 메서드에 매핑
import org.springframework.web.bind.annotation.PostMapping;                       // HTTP POST 요청을 이 메서드에 매핑
import org.springframework.web.bind.annotation.RequestBody;                       // 요청 JSON 본문을 자바 객체(DTO)로 변환해 받음
import org.springframework.web.bind.annotation.RequestMapping;                    // 클래스 공통 경로(/api/paper/orders) prefix 지정
import org.springframework.web.bind.annotation.ResponseStatus;                    // 성공 시 돌려줄 상태코드 지정 (여기선 201)
import org.springframework.web.bind.annotation.RestController;                    // REST 컨트롤러 표시 (반환값 → JSON)

import com.example.futurespapertrading.auth.User;                          // email로 조회한 유저 엔티티 (id를 꺼내려고)
import com.example.futurespapertrading.auth.UserRepository;                // email → User 조회 (내 user_id 얻기)
import com.example.futurespapertrading.paper.dto.CreateOrderRequest;      // 주문 생성 요청 본문 DTO
import com.example.futurespapertrading.paper.dto.OrderResponse;           // 주문 결과 응답 DTO (요약)

import jakarta.validation.Valid;          // @RequestBody의 DTO 검증 트리거(@Pattern 등 위반 시 자동 400)
import reactor.core.publisher.Flux;       // 0개 이상의 결과를 비동기로 흘려보내는 리액티브 상자 (주문 목록)
import reactor.core.publisher.Mono;       // 0~1개의 결과를 비동기로 흘려보내는 리액티브 상자

// 모의 주문 HTTP 입구. 엔드포인트 2개 — POST(시장가·지정가 주문 생성, C·E단계) / GET(내 주문 목록, D단계).
//   · HTTP 입출력만 담당: 요청 받기 → 현재 유저 확인(currentUserId) → PaperOrderService에 위임 → 응답.
//     실제 체결 판정·저장 로직은 PaperOrderService(서비스 계층)가 맡는다. (F의 취소·G의 matcher가 같은 로직을 재사용하려고 분리)
//   · 비로그인 차단(401)은 SecurityConfig의 .anyExchange().authenticated()가 자동 처리 → 여기선 신경 안 써도 됨.
@RestController
@RequestMapping("/api/paper/orders")
public class PaperOrderController {

    // ── 의존성 주입: 스프링이 생성자로 채워준다 ──
    private final PaperOrderService orderService;            // 주문 처리(체결·저장) 비즈니스 로직 — 실제 일은 여기로 위임
    private final UserRepository userRepository;             // email → 유저(=내 user_id) 조회

    public PaperOrderController(PaperOrderService orderService,
                                UserRepository userRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    // POST /api/paper/orders — 주문 생성. 성공 시 201 Created + OrderResponse(JSON).
    //   @Valid = CreateOrderRequest의 검증 규칙 위반 시 스프링이 자동 400 (여기 도달 전 차단).
    //   반환 Mono<OrderResponse> = "나중에 OrderResponse 1개를 흘려보낼 약속". 이 메서드는 파이프라인만 짜서 return하고,
    //     실제 실행은 WebFlux가 이 Mono를 구독(subscribe)할 때 일어난다. (스트림의 '게으름'과 같은 원리)
    @PostMapping
    // @ResponseStatus(HttpStatus.CREATED) = 이 메서드가 '정상 완료'되면 HTTP 응답 상태코드를 201로 못박는다.
    //   · @ResponseStatus  = "성공 시 돌려줄 상태코드"를 지정하는 애너테이션. 안 붙이면 본문 있는 성공은 기본 200 OK가 된다.
    //   · HttpStatus       = 200·201·400·401·503 같은 HTTP 상태코드를 '이름'으로 모아둔 enum (숫자 대신 의미로 쓰게).
    //   · HttpStatus.CREATED = 그중 201. "POST 요청으로 새 자원(여기선 주문 1건)이 생성됐다"를 뜻하는 관례적 코드.
    //       → 그냥 200(OK) 대신 201(Created)을 쓰는 이유: "조회/수정"이 아니라 "새로 만들었다"를 상태코드만으로 알리는 REST 관례.
    //   ※ 주의: 이 201은 끝까지 성공했을 때만 적용된다. 도중에 400(입력검증 위반)·503(시장가인데 호가 없음)·401(비로그인) 등
    //          에러가 나면 그쪽 상태코드가 우선하고, 이 201은 무시된다. (성공 경로의 '기본 성공 코드'를 정하는 것일 뿐)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        // 시장가·지정가 모두 받는다. (지정가의 limitPrice 필수·양수는 @Valid가 DTO에서 이미 걸러줌 → 여기 닿으면 통과한 것)
        //   currentUserId()는 Mono<Long>(아직 안 온 user_id). flatMap = 그 값이 준비되면 orderService.placeOrder로 이어 실행.
        //   placeOrder도 Mono를 돌려주므로 flatMap(평탄화)을 쓴다 — map이면 Mono<Mono<OrderResponse>>가 돼버린다.
        return currentUserId().flatMap(userId -> orderService.placeOrder(req, userId));
    }

    // GET /api/paper/orders — 내 주문 목록(최신순). 비로그인은 SecurityConfig가 컨트롤러 닿기 전 401로 차단.
    //   currentUserId()로 현재 유저 id만 확인하고, 실제 목록 조회·응답 변환은 PaperOrderService.listOrders에 위임한다.
    //   그래서 컨트롤러는 Repository를 직접 알 필요 없이 HTTP 입구 역할만 맡는다.
    @GetMapping
    public Flux<OrderResponse> list() {
        return currentUserId()                         // Mono<Long> (현재 로그인 유저 id)
                .flatMapMany(orderService::listOrders); // → Flux<OrderResponse>. Mono→Flux라 flatMap이 아닌 flatMapMany
        // ── 참고: 위 flatMapMany에 넘긴 메서드참조의 정체 = '동작의 객체화' ──
        //   자바는 '동작(메서드)' 자체는 값처럼 못 넘기지만 '객체(참조)'는 넘길 수 있다(함수가 1급 시민이 아님).
        //   그래서 동작을 '객체'로 만들어 넘긴다(=객체화). 그 객체의 타입이 추상 메서드 '딱 1개'인 인터페이스(=함수형 인터페이스)다.
        //   그 하나가 보장되니 람다·메서드참조로 그 객체를 즉석에서 만들 수 있다 (람다 ≡ new 함수형인터페이스(){...}의 줄임 — 몸통은 그 유일 메서드의 내용).
        //   즉 람다/메서드참조 = 그 인터페이스를 구현한 '객체'.
        //   예) 위 orderService::listOrders 는 Function<Long, Flux<OrderResponse>>의 apply를 구현한 '객체'로 들어간 것.
    }

    // 현재 로그인 유저의 user_id 꺼내기.
    //   레시피: SecurityContext(인증정보) → 그 이름(=로그인 식별자인 email) → DB 조회 → id.
    //   AuthController.me()가 쓰는 바로 그 레시피다 (me()는 끝에서 UserResponse를, 여기선 id만 뽑는 차이뿐).
    //   /api/paper/orders는 인증 필수 경로라, 비로그인 요청은 Security 필터가 '컨트롤러에 닿기 전에' 401로 끊는다.
    //   → 그래서 이 컨트롤러 코드(create()/currentUserId())가 실행된다는 것 자체가 로그인 통과를 뜻한다.
    //     ('여기 닿으면' = 요청이 필터를 통과해 이 컨트롤러 메서드까지 도달했다면. 그래서 아래 인증정보 추출에 null 걱정이 없다.)
    //   map vs flatMap 구분: 변환 결과가 '그냥 값'이면 map, '또 다른 Mono'면 flatMap을 쓴다.
    //     (flatMap을 안 쓰면 Mono 안에 Mono가 겹쳐 Mono<Mono<User>>처럼 돼버린다 → flatMap이 한 겹으로 펴줌)
    private Mono<Long> currentUserId() {
        // 보안 정보는 3겹으로 포개져 있고, 이 한 줄이 그 겹을 차례로 깐다 (Holder → Context → Authentication):
        //   ReactiveSecurityContextHolder = '보관소' — 인증정보를 어떻게/어디서 꺼내느냐(저장 방식)를 담당.
        //       (Servlet은 ThreadLocal에 두지만, 리액티브는 요청이 스레드를 넘나들어 Reactor Context에서 꺼낸다)
        //   SecurityContext               = '표준 그릇' — 보안 상태를 담는 규격. 저장 방식이 바뀌어도 이 규격은 고정.
        //   Authentication                = '신원증' — 실제 신원 데이터(이름=email·권한 등). getName()으로 email을 읽는다.
        //   ※ .getContext()는 값이 아니라 Mono<SecurityContext>를 준다 → 그래서 아래 .map/.flatMap으로 Mono 안에서 꺼내 이어붙인다.
        return ReactiveSecurityContextHolder.getContext()      // → Mono<SecurityContext>
                .map(ctx -> ctx.getAuthentication().getName()) // 식별자(email) — 결과가 String(값)이라 map
                .flatMap(userRepository::findByEmail)          // email → Mono<User> — 결과가 Mono라 flatMap
                .map(User::id);                                // User → user_id(Long) — 결과가 값이라 map
    }
}
