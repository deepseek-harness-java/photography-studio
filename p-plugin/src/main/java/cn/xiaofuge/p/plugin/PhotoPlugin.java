package cn.xiaofuge.p.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 摄影预约管家插件：把 photography-studio REST API 注册为 DSH Agent 工具 */
public class PhotoPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "photo-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public PhotoPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new PlanListTool(),
                new PhotographerListTool(),
                new BookTool(),
                new OrderInfoTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("photo-capabilities", 20, """
                ## AI 摄影预约管家（摄影工作室 · 2026-09-25）
                - 查套餐 → plan_list（5 个套餐价格与时长：证件照快拍129/个人写真688/情侣闺蜜写真888/亲子全家福988/
                  婚礼跟拍3888；工作日 9 折）
                - 查摄影师 → photographer_list（3 位摄影师特长/评分/在单量）
                - 预约拍摄 → book（customer/phone/plan/shootDate 必填，photographerId 可选默认阿哲 G01；
                  必须先复述套餐、价格、摄影师、拍摄时间请顾客确认后才能调用；成功报单号）
                - 订单查询 → order_info（orderId：F5001 格式；套餐/摄影师/时间/金额/状态）
                - 问运营 → stats（总单量/进行中/已出片/营收与预计营收/分套餐分摄影师分布/营销建议）
                - 回答要求：
                  1) 预约前必须复述要素（套餐/价格/摄影师/时间）请顾客确认
                  2) 预约结果必报单号与拍摄时间
                  3) 拍摄提醒：提前 15 分钟到店试装；可自带服装道具；精修张数套餐内含，加片另计
                  4) 价格与优惠只转述工具返回，禁止编造折扣
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: photo tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("PHOTO_APP_BASE_URL", "http://127.0.0.1:18114")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class PlanListTool extends AbstractTool {
        @Override public String name() { return "plan_list"; }
        @Override public String description() {
            return "拍摄套餐价目表：5 个套餐的价格与时长（证件照/写真/情侣/亲子/婚礼跟拍），含优惠规则。"
                    + "报价、预约前必查。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/plans", args));
        }
    }

    private class PhotographerListTool extends AbstractTool {
        @Override public String name() { return "photographer_list"; }
        @Override public String description() {
            return "摄影师列表：姓名/特长/评分/在单量。顾客挑摄影师、问谁拍得好时调用。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/photographers", args));
        }
    }

    private class BookTool extends AbstractTool {
        @Override public String name() { return "book"; }
        @Override public String description() {
            return "预约拍摄：customer（预约人）/phone（联系电话）/plan（套餐）/shootDate（拍摄时间）必填，"
                    + "photographerId（摄影师 G01-G03）可选默认 G01。必须先复述套餐、价格、摄影师、时间经顾客确认后才能调用。"
                    + "成功返回单号。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customer", stringSchema("预约人姓名"))
                    .prop("phone", stringSchema("联系电话"))
                    .prop("plan", stringSchema("套餐：证件照快拍 / 个人写真 / 情侣/闺蜜写真 / 亲子全家福 / 婚礼跟拍"))
                    .prop("photographerId", stringSchema("摄影师编号 G01-G03，可选，默认 G01 阿哲"))
                    .prop("shootDate", stringSchema("拍摄日期时间，如：周五 14:00"))
                    .required("customer", "phone", "plan", "shootDate")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"customer\":\"" + json(str(args, "customer"))
                    + "\",\"phone\":\"" + json(str(args, "phone"))
                    + "\",\"plan\":\"" + json(str(args, "plan"))
                    + "\",\"photographerId\":\"" + json(str(args, "photographerId"))
                    + "\",\"shootDate\":\"" + json(str(args, "shootDate")) + "\"}";
            return ok(post("/api/book", body, args));
        }
    }

    private class OrderInfoTool extends AbstractTool {
        @Override public String name() { return "order_info"; }
        @Override public String description() {
            return "订单查询：orderId 必填（F5001 格式）。返回套餐/摄影师/时间/金额/状态（已预订、拍摄中、已出片）。"
                    + "何时必须调用：顾客问订单、问出片没。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("orderId", stringSchema("订单号，如 F5001"))
                    .required("orderId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/order?orderId=" + java.net.URLEncoder.encode(str(args, "orderId"), StandardCharsets.UTF_8), args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：总单量/进行中/已出片/营收与预计营收/分套餐分摄影师分布/营销建议。"
                    + "何时必须调用：问今天运营、问单量与营收。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
