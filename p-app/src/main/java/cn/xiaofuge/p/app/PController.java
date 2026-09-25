package cn.xiaofuge.p.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 摄影预约管家 REST 接口。
 * 提供：套餐价目 / 摄影师列表 / 预约拍摄 / 订单查询 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class PController {

    private final PStore store;

    public PController(PStore store) {
        this.store = store;
    }

    /** 套餐价目表 */
    @GetMapping("/plans")
    public Map<String, Object> plans() {
        return store.planList();
    }

    /** 摄影师列表 */
    @GetMapping("/photographers")
    public Map<String, Object> photographers() {
        return store.photographerList();
    }

    /** 预约拍摄 */
    @PostMapping("/book")
    public Map<String, Object> book(@RequestBody Map<String, String> body) {
        return store.book(body.getOrDefault("customer", ""), body.getOrDefault("phone", ""),
                body.getOrDefault("plan", ""), body.getOrDefault("photographerId", ""),
                body.getOrDefault("shootDate", ""));
    }

    /** 订单查询 */
    @GetMapping("/order")
    public Map<String, Object> orderInfo(@RequestParam(required = false) String orderId) {
        return store.orderInfo(orderId == null ? "" : orderId);
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return store.stats();
    }
}
