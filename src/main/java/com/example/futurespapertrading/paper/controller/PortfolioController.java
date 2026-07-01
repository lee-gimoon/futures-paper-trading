package com.example.futurespapertrading.paper.controller;
import com.example.futurespapertrading.paper.service.PortfolioService;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.futurespapertrading.auth.domain.User;
import com.example.futurespapertrading.auth.repository.UserRepository;
import com.example.futurespapertrading.paper.dto.FillResponse;
import com.example.futurespapertrading.paper.dto.PortfolioResponse;
import com.example.futurespapertrading.paper.dto.SetLeverageRequest;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 9단계 계좌 화면 HTTP 입구. 엔드포인트 2개 — GET 계좌(현금·실현/미실현 PnL·포지션) / GET 체결 내역.
//   둘 다 로그인 필수. 비로그인 401은 SecurityConfig의 .anyExchange().authenticated()가 컨트롤러 닿기 전에 처리한다.
//   주문 생성·목록·취소는 PaperOrderController(/api/paper/orders)에 그대로 있다 — 여기는 '결과를 계좌로 보는' 쪽만 담당.
@RestController
@RequestMapping("/api/paper")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final UserRepository userRepository;

    public PortfolioController(PortfolioService portfolioService,
                               UserRepository userRepository) {
        this.portfolioService = portfolioService;
        this.userRepository = userRepository;
    }

    // GET /api/paper/account — 내 계좌 한 화면분(잔고·실현/미실현 PnL·포지션).
    @GetMapping("/account")
    public Mono<PortfolioResponse> account() {
        return currentUserId().flatMap(portfolioService::getPortfolio);
    }

    // GET /api/paper/fills — 내 체결 내역(오름차순, 프론트가 최신순으로 뒤집어 표시).
    @GetMapping("/fills")
    public Flux<FillResponse> fills() {
        return currentUserId().flatMapMany(portfolioService::listFills);
    }

    // PUT /api/paper/account/leverage — 레버리지 변경(1·3·5·10·20·50). 변경 후 갱신된 계좌 화면을 돌려준다.
    //   현재 API는 주문 요청마다 leverage를 받지 않고, 사용자가 미리 선택한 계좌 설정을 다음 주문에 적용한다.
    //   그래서 레버리지는 주문이 생기기 전에도 존재해야 하는 "다음 신규 주문용 현재 설정값"이다.
    //   이 값을 싱글톤 서비스 필드나 프론트 상태에만 두면 새로고침·재로그인·서버 재시작 때 잃어버리므로,
    //   사용자가 레버리지 버튼을 누를 때마다 현재 사용자 계좌의 paper_accounts.leverage 값으로 DB에 저장한다.
    //   주문 생성 시에는 userId로 paper_accounts 행을 조회해 그 사용자 행의 leverage만 읽고,
    //   그 DB 값을 다음 신규 주문에 적용한 뒤 paper_orders.leverage에 복사해 주문 시점 값으로 박제한다.
    @PutMapping("/account/leverage")
    public Mono<PortfolioResponse> setLeverage(@Valid @RequestBody SetLeverageRequest req) {
        return currentUserId().flatMap(userId -> portfolioService.setLeverage(userId, req.leverage()));
    }

    // 현재 로그인 유저의 user_id 꺼내기 (PaperOrderController·AuthController.me()와 같은 레시피:
    //   SecurityContext → 이름(email) → DB 조회 → id). 인증 필수 경로라 여기 닿으면 로그인 통과가 보장된다.
    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(userRepository::findByEmail)
                .map(User::id);
    }
}
