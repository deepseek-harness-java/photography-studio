package cn.xiaofuge.p.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 摄影预约数据中心：套餐/摄影师/订单/统计 */
@Component
public class PStore {

    /** 拍摄套餐：套餐/价格(元)/时长(小时)/说明 */
    static final Map<String, Object[]> PLANS = new LinkedHashMap<>();
    static {
        PLANS.put("证件照快拍", new Object[]{129.0, 1, "含精修 1 张，1 小时出片"});
        PLANS.put("个人写真", new Object[]{688.0, 2, "服装 2 套，精修 9 张，含底片"});
        PLANS.put("情侣/闺蜜写真", new Object[]{888.0, 2, "双人，服装各 2 套，精修 12 张"});
        PLANS.put("亲子全家福", new Object[]{988.0, 2, "最多 4 人，含道具，精修 12 张"});
        PLANS.put("婚礼跟拍", new Object[]{3888.0, 8, "双机位，全程跟拍，精修 60 张"});
    }

    /** 摄影师：编号/姓名/特长/评分 */
    static final Map<String, Object[]> PHOTOGRAPHERS = new LinkedHashMap<>();
    static {
        PHOTOGRAPHERS.put("G01", new Object[]{"阿哲", "人像/写真", 4.9});
        PHOTOGRAPHERS.put("G02", new Object[]{"莉娜", "亲子/纪实", 5.0});
        PHOTOGRAPHERS.put("G03", new Object[]{"大鹏", "婚礼/活动", 4.8});
    }

    public static class Order {
        public String id; public String customer; public String phone;
        public String plan; public String photographer; public String shootDate;
        public double total; public String status; // 已预订 / 拍摄中 / 已出片
    }

    public final List<Order> orders = new ArrayList<>();
    private int orderSeq = 5001;

    public PStore() { seed(); }

    private void seed() {
        orders.add(o("滕先生", "13800222222", "个人写真", "G01", "周四 14:00", "已预订"));
        orders.add(o("岑女士", "13800333333", "亲子全家福", "G02", "周四 10:00", "拍摄中"));
        orders.add(o("殷先生", "13800444444", "证件照快拍", "G03", "周三 11:00", "已出片"));
    }

    private Order o(String customer, String phone, String plan, String photographerId, String shootDate, String status) {
        Order x = new Order(); x.id = "F" + orderSeq++; x.customer = customer; x.phone = phone;
        x.plan = plan; x.photographer = String.valueOf(PHOTOGRAPHERS.get(photographerId)[0]);
        x.shootDate = shootDate;
        Object[] p = PLANS.get(plan);
        x.total = p != null ? (Double) p[0] : 0;
        x.status = status; return x;
    }

    /** 套餐价目表 */
    public Map<String, Object> planList() {
        List<Map<String, Object>> list = new ArrayList<>();
        PLANS.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("plan", k); m.put("price", v[0]); m.put("hours", v[1]); m.put("desc", v[2]); list.add(m); });
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("plans", list);
        r.put("note", "工作日拍摄享 9 折，加精修 30 元/张");
        return r;
    }

    /** 摄影师列表 */
    public Map<String, Object> photographerList() {
        List<Map<String, Object>> list = PHOTOGRAPHERS.entrySet().stream()
                .map(e -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", e.getKey()); m.put("name", e.getValue()[0]);
                    m.put("skill", e.getValue()[1]); m.put("rating", e.getValue()[2]);
                    m.put("activeOrders", orders.stream().filter(o -> o.photographer.equals(e.getValue()[0])
                            && !"已出片".equals(o.status)).count());
                    return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("photographers", list);
        return r;
    }

    /** 预约拍摄 */
    public synchronized Map<String, Object> book(String customer, String phone, String plan, String photographerId, String shootDate) {
        if (customer == null || customer.isBlank())
            return Map.of("ok", false, "msg", "请提供预约人姓名");
        Object[] p = PLANS.get(plan);
        if (p == null) return Map.of("ok", false, "msg", "套餐 " + plan + " 不在价目表，可选：" + String.join("/", PLANS.keySet()));
        if (phone == null || phone.isBlank())
            return Map.of("ok", false, "msg", "请提供联系电话，方便摄影师沟通拍摄方案");
        if (shootDate == null || shootDate.isBlank())
            return Map.of("ok", false, "msg", "请提供拍摄日期时间（如：周五 14:00）");
        String phName;
        if (photographerId == null || photographerId.isBlank()) {
            phName = String.valueOf(PHOTOGRAPHERS.get("G01")[0]); // 默认阿哲
        } else {
            var gEntry = PHOTOGRAPHERS.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(photographerId)).findFirst().orElse(null);
            if (gEntry == null) return Map.of("ok", false, "msg", "摄影师 " + photographerId + " 不存在，可选：" + String.join("/", PHOTOGRAPHERS.keySet()));
            phName = String.valueOf(gEntry.getValue()[0]);
        }
        Order x = new Order(); x.id = "F" + orderSeq++; x.customer = customer; x.phone = phone;
        x.plan = plan; x.photographer = phName; x.shootDate = shootDate;
        x.total = (Double) p[0]; x.status = "已预订";
        orders.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", customer);
        r.put("plan", plan); r.put("photographer", phName); r.put("shootDate", shootDate);
        r.put("total", x.total); r.put("hours", p[1]);
        r.put("msg", "预约成功！单号 " + x.id + "，" + plan + " ¥" + x.total + "，摄影师 " + phName + "，" + shootDate + " 到店，预计拍摄 " + p[1] + " 小时");
        return r;
    }

    /** 订单查询 */
    public Map<String, Object> orderInfo(String orderId) {
        Order x = orders.stream().filter(o -> o.id.equalsIgnoreCase(orderId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "订单 " + orderId + " 不存在，当前共 " + orders.size() + " 单");
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", x.customer);
        r.put("plan", x.plan); r.put("photographer", x.photographer); r.put("shootDate", x.shootDate);
        r.put("total", x.total); r.put("status", x.status);
        if ("已出片".equals(x.status)) r.put("msg", "成片已上传云端相册，凭手机号后四位可下载");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byPlan = new LinkedHashMap<String, Object>();
        for (String s : PLANS.keySet()) {
            long n = orders.stream().filter(o -> s.equals(o.plan)).count();
            if (n > 0) byPlan.put(s, n + " 单");
        }
        Map<String, Object> byPhotographer = new LinkedHashMap<String, Object>();
        for (var e : PHOTOGRAPHERS.entrySet()) {
            long n = orders.stream().filter(o -> o.photographer.equals(e.getValue()[0])).count();
            byPhotographer.put(String.valueOf(e.getValue()[0]), n + " 单");
        }
        long ongoing = orders.stream().filter(o -> "已预订".equals(o.status) || "拍摄中".equals(o.status)).count();
        double revenue = orders.stream().filter(o -> "已出片".equals(o.status)).mapToDouble(o -> o.total).sum();
        double expected = orders.stream().filter(o -> !"已出片".equals(o.status)).mapToDouble(o -> o.total).sum();
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("totalOrders", orders.size());
        r.put("ongoing", ongoing);
        r.put("done", orders.stream().filter(o -> "已出片".equals(o.status)).count());
        r.put("revenue", revenue);
        r.put("expectedRevenue", expected);
        r.put("byPlan", byPlan);
        r.put("byPhotographer", byPhotographer);
        r.put("advice", "毕业季与十一是写真高峰可提前开团；婚礼跟拍客单价高建议绑定婚庆渠道；出片 48 小时内交付可提升好评率");
        return r;
    }
}
