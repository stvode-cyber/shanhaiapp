import SwiftUI

@main
struct iOSApp: App {
    @StateObject private var game = GameState()
    var body: some Scene {
        WindowGroup {
            ContentView().environmentObject(game)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var game: GameState
    @State private var encounter: Monster?
    @State private var fromSecret = false
    @State private var didLoad = false
    var body: some View {
        TabView {
            ExploreView(encounter: $encounter, fromSecret: $fromSecret)
                .tabItem { Label("探索", systemImage: "location") }
            CodexView()
                .tabItem { Label("图鉴", systemImage: "book") }
            BattleView()
                .tabItem { Label("对战", systemImage: "flame") }
            QuestView()
                .tabItem { Label("任务", systemImage: "checklist") }
            CultivationView()
                .tabItem { Label("培养", systemImage: "star") }
            ForgeView()
                .tabItem { Label("炼妖", systemImage: "wand.and.stars") }
        }
        .fullScreenCover(item: $encounter) { m in
            CatchView(monster: m, fromSecret: fromSecret)
        }
        .onAppear {
            // 启动期：恢复存档 → 跨天重置 → 落盘
            if !didLoad {
                if let s = GameState.loadSave() { game.applySaveData(s) }
                game.maybeResetDaily(todayString())
                game.tickSeason(Int64(Date().timeIntervalSince1970 * 1000))   // 赛季推进
                game.save()
                didLoad = true
            }
        }
    }
}

// MARK: - 探索
struct ExploreView: View {
    @EnvironmentObject var game: GameState
    @Binding var encounter: Monster?
    @Binding var fromSecret: Bool
    @State private var term: SolarTerm = .none
    @State private var night = false
    @State private var realmResult: SecretRealmResult? = nil
    @State private var homesteadMsg: String? = nil
    @State private var seasonMsg: String? = nil
    @State private var rankMsg: String? = nil
    @State private var honorMsg: String? = nil
    @State private var sectMsg: String? = nil
    @State private var exportText: String? = nil
    @State private var importText: String = ""
    @State private var importMsg: String? = nil
    let nodes = [
        ("古庙", "灯影摇曳", "jing_denglong"),
        ("老槐树", "草木成精", "jing_saozhou"),
        ("城隍桥", "夜行狐妖", "yao_hu"),
        ("东海之滨", "神兽栖潮", "lingshou_zhuque"),
    ]

    private func themeCN(_ t: RegionTheme) -> String {
        switch t { case .shanlin: return "山林"; case .shuiyu: return "水域"; case .gumu: return "古墓"; case .donghai: return "东海" }
    }
    private func elemCN(_ e: Element) -> String {
        switch e { case .metal: return "金"; case .wood: return "木"; case .earth: return "土"; case .water: return "水"; case .fire: return "火"; case .wind: return "风"; case .thunder: return "雷" }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("巡妖使 · 出行").font(.title2).bold()
                    Text("把整座城市变成《山海经》").font(.subheadline).foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        ResourceChip(label: "香火", value: "\(game.incense)", color: .brown)
                        ResourceChip(label: "妖丹", value: "\(game.codex.regionCaught)", color: .blue)
                        ResourceChip(label: "秘境", value: "\(game.secretRealmLeft)", color: .orange)
                    }
                    Text("灵脉节点").font(.headline)
                    ForEach(nodes, id: \.0) { node in
                        Button { fromSecret = false; encounter = MonsterPool.byId(node.2) } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(node.0).bold()
                                    Text(node.1).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("探查 ›").foregroundStyle(.red).bold()
                            }
                            .padding()
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                        }
                        .foregroundStyle(.primary)
                    }
                    Button { fromSecret = false; encounter = MonsterPool.randomEncounter() } label: {
                        Text("出门探索 · 随机遇妖").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    // ── 新手引导（GDD 十八） ──
                    if game.tutorialActive() {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text("新手引导").font(.headline)
                                Spacer()
                                Button("跳过") { game.skipTutorial(); game.save() }
                                    .font(.caption)
                            }
                            ForEach(TutorialStep.byOrder, id: \.self) { step in
                                let done = step.order <= game.tutorialStep
                                let cur = step.order == game.currentTutorialStep()?.order
                                HStack(spacing: 6) {
                                    Text(done ? "✓" : (cur ? "▶" : "·"))
                                        .foregroundStyle(done ? .green : .gray)
                                    Text("\(step.order). \(step.title)\(step.forceable ? "（引导）" : "")")
                                        .font(.caption)
                                        .foregroundStyle(cur ? .blue : .secondary)
                                }
                            }
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                    }

                    // ── 在家秘境（GDD 三·探索双模式·在家秘境） ──
                    Text("在家秘境").font(.headline)
                    Button {
                        if let res = game.enterSecretRealm() {
                            game.save()
                            realmResult = res
                            fromSecret = true
                            encounter = MonsterPool.byId(res.spawns.first ?? "")
                        }
                    } label: {
                        if game.secretRealmLeft > 0 {
                            Text("入秘境（剩 \(game.secretRealmLeft) 次 · 免费）").frame(maxWidth: .infinity)
                        } else {
                            Text("入秘境（耗 \(SecretRealmEngine.INCENSE_COST) 香火）").frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.bordered)
                    .disabled(game.secretRealmLeft <= 0 && game.incense < SecretRealmEngine.INCENSE_COST)
                    if !game.unlockedFeatures().contains(.realm) {
                        Text("第 2 日签到后解锁").font(.caption).foregroundStyle(.secondary)
                    }
                    if let res = realmResult {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("主题：\(themeCN(res.theme)) · 五行偏向：\(elemCN(res.element))").font(.caption).bold()
                            Text("刷妖一览：\(res.spawns.compactMap { MonsterPool.byId($0)?.name }.joined(separator: " "))").font(.caption).foregroundStyle(.secondary)
                        }
                        .padding(8)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                    }

                    // ── 家园洞府（GDD 十九·长线留存：放置产香火/妖丹 + 阵法风水加成） ──
                    Text("家园洞府").font(.headline)
                    let incRate = game.homesteadIncenseRate()
                    let pillRate = game.homesteadPillRate()
                    let upCost = HomesteadEngine.upgradeCost(game.homesteadLevel)
                    Text("阵法风水 Lv.\(game.homesteadLevel) · 时产：香火 \(String(format: "%.1f", incRate)) · 妖丹 \(String(format: "%.1f", pillRate))")
                        .font(.caption).foregroundStyle(.secondary)
                    Text("（离线封顶 12h；阵法每级 +10% 香火/+0.1 妖丹，并 +\(Int(game.homesteadAttackBonus() * 100))% 攻击）")
                        .font(.caption2).foregroundStyle(.secondary)
                    HStack(spacing: 12) {
                        Button {
                            let now = Int64(Date().timeIntervalSince1970 * 1000)
                            let r = game.collectHomestead(now)
                            game.save()
                            homesteadMsg = (r.incense > 0 || r.pills > 0)
                                ? "收取 +香火 \(r.incense) / +妖丹 \(r.pills)\(r.capped ? "（已封顶 12h）" : "")"
                                : "暂无可收取产出"
                        } label: { Text("收取产出").frame(maxWidth: .infinity) }
                        .buttonStyle(.bordered)
                        Button {
                            if game.upgradeHomestead() {
                                game.save()
                                homesteadMsg = "阵法升至 Lv.\(game.homesteadLevel)"
                            }
                        } label: { Text("升阵法（\(upCost) 妖丹）").frame(maxWidth: .infinity) }
                        .buttonStyle(.bordered)
                        .disabled(!game.canUpgradeHomestead())
                    }
                    if let msg = homesteadMsg {
                        Text(msg).font(.caption).foregroundStyle(.green)
                    }

                    // ── 赛季通行证（GDD 十九·长线留存：90 天双轨） ──
                    Text("赛季通行证").font(.headline)
                    let sNow = Int64(Date().timeIntervalSince1970 * 1000)
                    let sIn = game.isSeasonInProgress(sNow)
                    let sTier = game.currentSeasonTier()
                    HStack {
                        Text("第\(game.seasonIndex)赛季").bold()
                        Spacer()
                        Text(sIn ? "剩 \(game.seasonDaysRemaining(sNow)) 天" : "已结束")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Text("档位 \(sTier)/\(SeasonEngine.tiers) · 经验 \(game.seasonXp)（每 \(SeasonEngine.xpPerTier) 经验升 1 档）")
                        .font(.caption).foregroundStyle(.secondary)
                    ProgressView(value: Double(sTier), total: Double(SeasonEngine.tiers))
                    if !game.hasSeasonPass {
                        Button {
                            if game.buySeasonPass(cost: 300) { game.save(); seasonMsg = "已购买赛季通行证，可领取付费轨道奖励" }
                        } label: { Text("购买通行证（300 灵玉）").frame(maxWidth: .infinity) }
                        .buttonStyle(.bordered)
                        .disabled(game.lingyu < 300)
                    } else {
                        Text("已拥有通行证 · 付费轨道已解锁").font(.caption).foregroundStyle(.green)
                    }
                    Button {
                        let rs = game.claimAllSeason()
                        let freeN = rs.filter { $0.claimedFree }.count
                        let paidN = rs.filter { $0.claimedPaid }.count
                        game.save()
                        seasonMsg = "领取成功：免费 \(freeN) 档\(game.hasSeasonPass ? " · 付费 \(paidN) 档" : "")"
                    } label: { Text("领取已解锁奖励").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                    if let msg = seasonMsg {
                        Text(msg).font(.caption).foregroundStyle(.green)
                    }

                    // ── 疲劳值 / 单日收益封顶（GDD 十六·公平与稳健） ──
                    Text("疲劳值 · 单日收益封顶").font(.headline)
                    let fMul = game.incomeMul()
                    HStack {
                        Text("疲劳 \(game.fatigue)/\(FatigueEngine.maxFatigue)").bold()
                        Spacer()
                        Text(game.fatigueLabel()).font(.caption)
                            .foregroundStyle(game.fatigue >= FatigueEngine.maxFatigue ? .red : .secondary)
                    }
                    Text("当前收益倍率 ×\(String(format: "%.1f", fMul))（收妖/对战/副本/签到累积，每日跨天重置）")
                        .font(.caption).foregroundStyle(.secondary)
                    ProgressView(value: Double(game.fatigue), total: Double(FatigueEngine.maxFatigue))

                    // ── 排位赛段位天梯（GDD 二十·竞技与荣誉） ──
                    Text("段位天梯").font(.headline)
                    HStack {
                        Text("当前段位：\(game.rankTierName())").bold()
                        Spacer()
                        Text("积分 \(game.rankPoints)/\(RankEngine.pointsToPromote)").font(.caption).foregroundStyle(.secondary)
                    }
                    Text("胜 +\(RankEngine.winPoints) / 负 -\(RankEngine.lossPoints)，满 \(RankEngine.pointsToPromote) 升段（ban/pick + meta 平衡由平台匹配层负责）")
                        .font(.caption).foregroundStyle(.secondary)
                    ProgressView(value: game.rankProgress(), total: 1.0)
                    HStack(spacing: 12) {
                        Button("模拟胜利") {
                            let r = game.rankedBattle(true)
                            rankMsg = r.promoted ? "胜利！晋升「\(RankEngine.tierName(r.tierIndex))」并获得荣誉奖励" : "胜利，积分 +\(RankEngine.winPoints)"
                            game.save()
                        }
                        Button("模拟失败") {
                            let r = game.rankedBattle(false)
                            rankMsg = r.demoted ? "失败，降级至「\(RankEngine.tierName(r.tierIndex))」" : "失败，积分 -\(RankEngine.lossPoints)"
                            game.save()
                        }
                        .buttonStyle(.bordered)
                    }
                    if let msg = rankMsg { Text(msg).font(.caption).foregroundStyle(.accent) }

                    // ── 成就 / 勋章 / 称号（GDD 二十·竞技与荣誉·展示型荣誉） ──
                    Text("成就 · 勋章 · 称号").font(.headline)
                    let achDone = game.achievementCount()
                    let achTotal = game.achievementTotal()
                    Text("成就进度 \(achDone) / \(achTotal)（来自任务·成就分类）").font(.caption)
                    let unlockedMedals = game.medals()
                    Text("已解锁勋章：\(unlockedMedals.isEmpty ? "暂无" : unlockedMedals.map { $0.name }.joined(separator: "、"))")
                        .font(.caption)
                    let curTitle = game.displayTitle()
                    Text("当前展示称号：\(curTitle.isEmpty ? "无" : "「\(curTitle)」")")
                        .font(.caption)
                        .foregroundStyle(.accent)
                    let titlesList = game.titles()
                    if titlesList.isEmpty {
                        Text("尚未解锁任何称号（继续游玩以解锁）").font(.caption)
                    } else {
                        ForEach(titlesList, id: \.id) { t in
                            HStack {
                                Text(t.name).font(.caption)
                                Spacer()
                                Button(action: {
                                    if game.equipTitle(t.id) { honorMsg = "已装备称号「\(t.name)」" }
                                }) {
                                    Text(game.equippedTitleId == t.id ? "已装备" : "装备")
                                }
                                .disabled(game.equippedTitleId == t.id)
                            }
                        }
                    }
                    if let hm = honorMsg { Text(hm).font(.caption).foregroundStyle(.accent) }

                    // ── 二十·宗门科技树（GDD 二十·公会建设 + 科技加成） ──
                    Text("宗门科技树").font(.headline)
                    let factionName: String = { switch game.faction {
                        case "MAOSHAN": return "茅山"; case "LONGHUSHAN": return "龙虎山"
                        case "GEZAO": return "阁皂山"; default: return "未择门" } }()
                    let b = game.sectBonuses()
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("派系：\(factionName) · 贡献 \(game.sectContribution)").font(.caption).foregroundStyle(.secondary)
                            Spacer()
                        }
                        Text("攻击 +\(Int(b.attack*100))% · 防御 +\(Int(b.defense*100))% · 体力 +\(Int(b.hp*100))% · 收妖 +\(Int(b.capture*100))% · 经验 +\(Int(b.exp*100))% · 收益 +\(Int(b.income*100))% · 据点贡献 +\(Int(b.stronghold*100))%")
                            .font(.caption2)
                        if game.faction == nil {
                            Text("先选择派系（上方「祈妖·副本·据点」），再通过「据点战」占领积累宗门贡献，即可研习科技。").font(.caption).foregroundStyle(.secondary)
                        } else {
                            ForEach(SectTechEngine.NODES, id: \.id) { node in
                                let lv = game.sectTechLevel(node.id)
                                let maxed = lv >= node.maxLevel
                                let cost = maxed ? 0 : SectTechEngine.researchCost(level: lv, node)
                                let can = game.canResearchSectTech(node.id)
                                let lockedByFaction = node.faction != nil && game.faction != node.faction?.rawValue
                                let prereqOk = SectTechEngine.prereqMet(node, game.sectTech)
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(node.name)\(node.faction != nil ? "（专属）" : "")").font(.caption)
                                        Text("Lv \(lv)/\(node.maxLevel) · \(node.desc)").font(.caption2).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Button(action: {
                                        if game.researchSectTech(node.id) {
                                            sectMsg = "已研习 \(node.name) → Lv \(game.sectTechLevel(node.id))"
                                            game.save()
                                        }
                                    }) {
                                        Text(maxed ? "满级" : (lockedByFaction ? "需\(factionLabel(node.faction!))" : (prereqOk ? "研习(\(cost))" : "需前置")))
                                    }
                                    .disabled(!can)
                                }
                            }
                        }
                        if let sm = sectMsg { Text(sm).font(.caption).foregroundStyle(.accent) }
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

                    // ── 活动（GDD·活动·节气复玩 / 年节限时 + 运营活动） ──
                    Text("限时活动").font(.headline)
                    let acts = game.currentActivities()
                    VStack(alignment: .leading, spacing: 8) {
                        if acts.isEmpty {
                            Text("当前无进行中的活动。").font(.caption).foregroundStyle(.secondary)
                        } else {
                            ForEach(acts, id: \.id) { a in
                                let parts = [
                                    a.captureMul > 1.0 ? "收妖 +\(Int((a.captureMul - 1) * 100))%" : nil,
                                    a.expMul > 1.0 ? "经验 +\(Int((a.expMul - 1) * 100))%" : nil,
                                    a.incomeMul > 1.0 ? "收益 +\(Int((a.incomeMul - 1) * 100))%" : nil,
                                ].compactMap { $0 }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(a.name).font(.caption)
                                    Text(a.desc).font(.caption2).foregroundStyle(.secondary)
                                    if !parts.isEmpty {
                                        Text(parts.joined(separator: " · ")).font(.caption).foregroundStyle(.accent)
                                    }
                                }
                            }
                        }
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

                    // ── 每日 0 点静默重置（GDD·每日语义收口，DailyResetEngine） ──
                    Text("每日 0 点刷新").font(.headline)
                    Text("每日 0 点（跨天）自动重置以下系统：").font(.caption).foregroundStyle(.secondary)
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(DailyResetEngine.targets, id: \.rawValue) { t in
                            Text("· \(t.desc)").font(.caption)
                        }
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

                    // ── 存档备份 · 云同步（跨端便携导出 / 导入，SaveExportEngine） ──
                    Text("存档备份 · 云同步").font(.headline)
                    Text("导出为便携备份码（双端格式一致），可粘贴到另一台设备恢复进度。").font(.caption).foregroundStyle(.secondary)
                    Button("生成并复制备份码") {
                        let code = SaveExportEngine.toPortable(game.toSaveData())
                        exportText = code
                        UIPasteboard.general.string = code
                        game.save()
                    }
                    if let exportText {
                        Text(String(exportText.prefix(80)) + (exportText.count > 80 ? "…" : "")).font(.caption2).foregroundStyle(.secondary)
                        Text("已复制到剪贴板。").font(.caption).foregroundStyle(.accent)
                    }
                    TextField("粘贴备份码", text: $importText)
                        .textFieldStyle(.roundedBorder)
                    Button("恢复进度") {
                        if let sd = SaveExportEngine.fromPortable(importText) {
                            game.applySaveData(sd)
                            game.save()
                            importMsg = "恢复成功"
                        } else {
                            importMsg = "备份码无效"
                        }
                    }
                    if let importMsg {
                        Text(importMsg).font(.caption).foregroundStyle(.accent)
                    }

                    // ── 户外灵脉 · LBS（坐标确定性刷怪演示；真实 GPS 由平台接入） ──
                    Text("户外灵脉 · LBS").font(.headline)
                    let demoLat = 30.25, demoLng = 120.16
                    let ctx = SpawnContext(lat: demoLat, lng: demoLng, hourOfDay: night ? 23 : 12, solarTerm: term)
                    let region = LocationEngine.resolveRegion(ctx)
                    let bias = LocationEngine.regionElementBias(region)
                    let integrity = LocationEngine.spawnIntegrity(ctx)
                    let spawns = LocationEngine.safeSpawns(ctx, 12)
                    let nearNode = LocationEngine.nearbyNode(ctx)
                    let ar = game.arStatus()
                    let today = todayString()
                    HStack(spacing: 8) {
                        ResourceChip(label: "地域", value: regionLabel(region), color: .green)
                        ResourceChip(label: "五行偏向", value: bias.label, color: .blue)
                        ResourceChip(label: night ? "子时" : "白昼", value: night ? "鬼类+" : "常规", color: .purple)
                    }
                    Text("灵脉信号：\(integrity.tier == .normal ? "正常" : (integrity.tier == .degraded ? "降品质 · \(integrity.reason)" : "异常 · \(integrity.reason)"))")
                        .font(.caption)
                        .foregroundStyle(integrity.tier == .normal ? .green : (integrity.tier == .degraded ? .orange : .red))
                    if let n = nearNode {
                        Text("靠近灵脉节点：\(n)").font(.caption).foregroundStyle(.red)
                    }
                    // 信誉分（GDD 十六·公平与稳健）：与 GPS 防作弊闭环
                    HStack(spacing: 8) {
                        let tier = game.reputationTier()
                        ResourceChip(label: "信誉", value: game.reputationLabel(),
                                     color: tier == .excellent ? .green : (tier == .observed ? .orange : .red))
                        if !game.canExploreLbs() {
                            Text("受限：仅可入秘境").font(.caption).foregroundStyle(.red)
                        }
                        Button("模拟 GPS 异常") {
                            game.reportIntegrity(IntegrityResult(ok: false, reason: "模拟瞬移", speedKmh: 0, tier: .rejected, degraded: true))
                        }.font(.caption)
                    }
                    HStack(spacing: 8) {
                        TermToggle(label: "平常", selected: term == .none) { term = .none }
                        TermToggle(label: "中元", selected: term == .zhongyuan) { term = .zhongyuan }
                        TermToggle(label: "春节", selected: term == .chunjie) { term = .chunjie }
                        TermToggle(label: night ? "切白昼" : "切子时", selected: false) { night.toggle() }
                    }
                    Text("附近灵脉渗出妖气（加权抽样）：").font(.caption).foregroundStyle(.secondary)
                    let counts = Dictionary(grouping: spawns, by: { $0 }).mapValues { $0.count }.sorted { $0.value > $1.value }
                    let top = Array(counts.prefix(6))
                    ScrollView(.horizontal) {
                        HStack(spacing: 6) {
                            ForEach(top.indices, id: \.self) { i in
                                let (id, c) = top[i]
                                if let m = MonsterPool.byId(id) {
                                    Text("\(m.name)×\(c)").font(.caption2)
                                        .padding(.horizontal, 8).padding(.vertical, 4)
                                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.blue.opacity(0.18)))
                                }
                            }
                        }
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("AR 收妖").bold()
                            Spacer()
                            Text(ar == .ready ? "就绪" : (ar == .permissionDenied ? "权限被拒" : "占位未启用")).font(.caption).foregroundStyle(.secondary)
                        }
                        Text("收妖判定逻辑已就绪（复用光环点按）。当前平台未启用 AR（ARKit/ARCore 由平台层接入，status() 默认 unsupported），接入后 UI 自动激活，操作与国风卷轴背景一致。").font(.caption).foregroundStyle(.secondary)
                        Button(ar == .ready ? "进入 AR 收妖" : "AR 收妖（待平台接入）") {
                            fromSecret = false; encounter = MonsterPool.randomEncounter()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(ar != .ready)
                        .frame(maxWidth: .infinity)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

                    // ── 祈妖 · 副本 · 据点（社交 / 经济入口） ──
                    Text("祈妖 · 副本 · 据点").font(.headline)
                    NavigationLink("祈妖抽卡", destination: GachaView()).buttonStyle(.borderedProminent)
                    NavigationLink("副本妖境", destination: DungeonView()).buttonStyle(.bordered)
                    Text("所属派系（影响据点归属，可后期转）").font(.caption).foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        ForEach(Faction.allCases, id: \.self) { f in
                            let sel = game.faction == f.rawValue
                            Button(factionLabel(f)) { game.joinFaction(f); game.save() }
                                .foregroundStyle(sel ? .red : .gray)
                                .font(sel ? .headline : .body)
                        }
                    }
                    let bonus = game.strongholdAttackBonus()
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("据点战").bold()
                            Spacer()
                            Text(game.strongholdNode != nil ? "已占领：\(game.strongholdNode!)（攻击 +\(Int((bonus - 1) * 100))%）" : "未占领").font(.caption).foregroundStyle(.secondary)
                        }
                        let capNode = LocationEngine.nearbyNode(SpawnContext(lat: 30.25, lng: 120.16, hourOfDay: 12, solarTerm: .none)) ?? "城隍桥"
                        Button(game.strongholdNode == nil ? "占领灵脉节点：\(capNode)（每日限一次）" : "今日已占领") {
                            game.captureNode(capNode, today: today); game.save()
                        }
                        .buttonStyle(.bordered)
                        .disabled(game.strongholdNode != nil)
                        .frame(maxWidth: .infinity)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                    let remain = 3 - game.shopIncenseBought
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("引妖香商店").bold()
                            Spacer()
                            Text("今日可购 \(remain) / 3").font(.caption).foregroundStyle(.secondary)
                        }
                        Text("30 灵玉 → +60 香火（日购上限 3，防通胀；付费只卖机会不卖战力）").font(.caption).foregroundStyle(.secondary)
                        Button("购买引妖香（30 灵玉 → +60 香火）") {
                            game.buyIncense(today: today); game.save()
                        }
                        .buttonStyle(.bordered)
                        .disabled(game.lingyu < 30 || remain <= 0)
                        .frame(maxWidth: .infinity)
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                }
                .padding()
            }
            .navigationTitle("探索")
        }
    }
}

struct ResourceChip: View {
    let label: String
    let value: String
    let color: Color
    var body: some View {
        VStack {
            Text(value).bold().foregroundStyle(color)
            Text(label).font(.caption2)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 10).fill(color.opacity(0.15)))
    }
}

// MARK: - LBS / 社交 辅助
func regionLabel(_ r: RegionTheme) -> String {
    switch r { case .shanlin: return "山林"; case .shuiyu: return "水域"; case .gumu: return "古墓"; case .donghai: return "东海" }
}
func factionLabel(_ f: Faction) -> String {
    switch f { case .maoshan: return "茅山"; case .longhushan: return "龙虎山"; case .gezao: return "阁皂山" }
}
struct TermToggle: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(label, action: action)
            .foregroundStyle(selected ? .red : .gray)
            .font(selected ? .headline : .body)
    }
}

// MARK: - 祈妖抽卡
struct GachaView: View {
    @EnvironmentObject var game: GameState
    @State private var results: [GachaResult] = []
    @State private var msg = ""
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("祈妖 · 抽签祈妖").font(.title2).bold()
                    Text("摇签筒 → 掉签 → 解签 → 亮妖。上上签出神、上签出仙、中签出灵；重复妖自动转妖丹。付费只卖机会，不卖战力。").font(.subheadline).foregroundStyle(.secondary)
                    HStack(spacing: 8) {
                        ResourceChip(label: "灵玉", value: "\(game.lingyu)", color: .purple)
                        ResourceChip(label: "妖丹", value: "\(game.yaodan)", color: .blue)
                    }
                    let pity = game.gachaSinceShen
                    Text("保底进度：距上次出神 \(pity) / 90 抽（第 50 抽起软保底提升神率）").font(.caption)
                    ProgressView(value: min(Double(pity) / 90.0, 1.0))
                    HStack(spacing: 12) {
                        Button("单抽 · \(GachaEngine.singleLingyu)灵玉") {
                            if let r = game.pullGacha(ten: false) { results = r; msg = "" } else { msg = "灵玉不足（单抽需 \(GachaEngine.singleLingyu)）" }
                            game.save()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(game.lingyu < GachaEngine.singleLingyu)
                        .frame(maxWidth: .infinity)
                        Button("十连 · \(GachaEngine.tenLingyu)灵玉") {
                            if let r = game.pullGacha(ten: true) { results = r; msg = "十连已出 \(r.filter { $0.rarity >= .ling }.count) 只及以上" } else { msg = "灵玉不足（十连需 \(GachaEngine.tenLingyu)）" }
                            game.save()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(game.lingyu < GachaEngine.tenLingyu)
                        .frame(maxWidth: .infinity)
                    }
                    if !msg.isEmpty { Text(msg).foregroundStyle(.red).font(.caption) }
                    Text("本次结果").font(.headline)
                    if results.isEmpty {
                        Text("尚未祈妖——摇一次签筒试试。").foregroundStyle(.secondary)
                    } else {
                        ForEach(results.indices, id: \.self) { i in
                            let res = results[i]
                            let m = MonsterPool.byId(res.monsterId)
                            let isNew = (m != nil) && !game.codex.hasAny(res.monsterId)
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(m?.name ?? res.monsterId).bold()
                                    Text("\(signLabel(res.sign)) · \(rarityLabel(res.rarity))").font(.caption).foregroundStyle(rarityColor(res.rarity))
                                }
                                Spacer()
                                Text(isNew ? "新妖入队" : "+\(GachaEngine.duplicateYaodan)妖丹").font(.caption).foregroundStyle(isNew ? .red : .gray)
                            }
                            .padding()
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("祈妖")
        }
    }
}

// MARK: - 副本妖境
struct DungeonView: View {
    @EnvironmentObject var game: GameState
    @State private var lastReward: DungeonReward? = nil
    @State private var lastDef: DungeonDef? = nil
    @State private var msg = ""
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("副本妖境").font(.title2).bold()
                    Text("进入消耗香火、每日限次。通关掉落妖丹 / 渡劫材料 / 符纸 / 外观，妖王有概率收服补全图鉴。").font(.subheadline).foregroundStyle(.secondary)
                    ResourceChip(label: "香火", value: "\(game.incense)", color: .brown)
                    let today = todayString()
                    ForEach(DungeonLibrary.all, id: \.id) { def in
                        let entered = game.dungeonEnteredToday[def.id] ?? 0
                        let reached = entered >= def.dailyLimit
                        let can = game.incense >= def.costIncense && !reached
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(def.name).bold()
                                Spacer()
                                Text(dungeonTypeLabel(def.type)).font(.caption).foregroundStyle(.red).bold()
                            }
                            Text(def.desc).font(.caption).foregroundStyle(.secondary)
                            Text("消耗 \(def.costIncense) 香火 · 每日 \(def.dailyLimit) 次（今日已 \(entered)）").font(.caption)
                            if def.bossMonsterId != nil {
                                Text("妖王：\(def.bossMonsterId!)（收服概率 \(Int(def.bossCaptureChance * 100))%）").font(.caption).foregroundStyle(.purple)
                            }
                            Button(reached ? "今日已达上限" : "进入副本") {
                                if let r = game.enterDungeon(def.id, today: today) { lastReward = r; lastDef = def; msg = "" } else { msg = "无法进入（香火不足或已达每日上限）" }
                                game.save()
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(!can)
                            .frame(maxWidth: .infinity)
                        }
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                    }
                    if !msg.isEmpty { Text(msg).foregroundStyle(.red).font(.caption) }
                }
                .padding()
            }
            .navigationTitle("副本妖境")
            .alert(isPresented: Binding(get: { lastReward != nil }, set: { if !$0 { lastReward = nil; lastDef = nil } })) {
                Alert(title: Text("\(lastDef?.name ?? "") · 通关"),
                      message: Text(dungeonRewardText(lastReward)),
                      dismissButton: .default(Text("确定")) { lastReward = nil; lastDef = nil })
            }
        }
    }
}

private func signLabel(_ s: SignTier) -> String {
    switch s { case .zhong: return "中签"; case .shang: return "上签"; case .shangShang: return "上上签" }
}
private func rarityLabel(_ r: Rarity) -> String {
    switch r { case .shen: return "神"; case .xian: return "仙"; case .ling: return "灵"; default: return "凡" }
}
private func dungeonTypeLabel(_ t: DungeonType) -> String {
    switch t { case .yiwen: return "山海异闻"; case .shilian: return "灵脉试炼"; case .duojie: return "渡劫秘境"; case .nianjie: return "年节限时" }
}
private func dungeonRewardText(_ r: DungeonReward?) -> String {
    guard let r = r else { return "" }
    var s = "妖丹 +\(r.yaodan)"
    if r.material > 0 { s += "\n渡劫材料 +\(r.material)" }
    s += "\n符纸：\(r.talisman ? "获得" : "未掉落")"
    s += "\n外观：\(r.skin ? "获得" : "未掉落")"
    if r.bossMonsterId != nil { s += "\n妖王：\(r.bossCaptured ? "收服成功！" : "未能收服")" }
    return s
}

// MARK: - 培养（等级 / 经验 / 突破 / 进化）
struct CultivationView: View {
    @EnvironmentObject var game: GameState
    @State private var tick = 0
    @State private var tribEffect: (name: String, times: Int)? = nil
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("妖宠培养").font(.title2).bold()
                    Text("妖丹 \(game.yaodan)（突破 / 渡劫消耗） · 拥有 \(game.owned.count) 只").font(.subheadline).foregroundStyle(.secondary)
                    if game.owned.isEmpty {
                        Text("还没有妖宠，先去「探索」收服。").padding()
                    } else {
                        ForEach(Array(game.owned.values), id: \.instanceId) { inst in
                            CultivateRow(inst: inst, yaodan: game.yaodan,
                                onTrain: { _ = game.gainExp(instanceId: inst.instanceId, 60); game.save(); tick += 1 },
                                onBreak: { _ = game.breakthroughMonster(instanceId: inst.instanceId); game.save(); tick += 1 },
                                onEvolve: { _ = game.evolveMonster(instanceId: inst.instanceId); game.save(); tick += 1 },
                                onTribulate: {
                                    let before = game.owned[inst.instanceId]?.tribulations ?? 0
                                    _ = game.tribulateMonster(instanceId: inst.instanceId); game.save()
                                    if let ni = game.owned[inst.instanceId], ni.tribulations > before {
                                        tribEffect = (name: MonsterPool.byId(inst.defId)?.name ?? inst.defId, times: ni.tribulations)
                                    }
                                    tick += 1
                                },
                                onTalent: { b in _ = game.assignTalent(instanceId: inst.instanceId, b); game.save(); tick += 1 })
                        }
                    }
                }
                .padding()
                .id(tick)
            }
            .navigationTitle("培养")
        }
        .overlay {
            if let fx = tribEffect {
                TribulationOverlay(name: fx.name, times: fx.times) { tribEffect = nil }
            }
        }
    }
}

struct CultivateRow: View {
    let inst: MonsterInstance
    let yaodan: Int
    let onTrain: () -> Void
    let onBreak: () -> Void
    let onEvolve: () -> Void
    let onTribulate: () -> Void
    let onTalent: (TalentBranch) -> Void
    var body: some View {
        let def = MonsterPool.byId(inst.defId)
        let cap = CultivationEngine.levelCap(inst)
        let need = CultivationEngine.expForLevel(inst.level)
        let ratio = inst.level >= cap ? 1.0 : min(Double(inst.exp) / Double(need), 1.0)
        let canBreak = CultivationEngine.canBreakthrough(inst)
        let breakCost = CultivationEngine.breakthroughCost(inst.breakthrough)
        let canEvolve = CultivationEngine.canEvolve(inst)
        let canTrib = CultivationEngine.canTribulate(inst)
        let tribCost = CultivationEngine.tribulateCost(inst)
        let maxTrib = def.map { CultivationEngine.maxTribulations($0.rarity) } ?? 0
        // ── 天赋 / 命格 ──
        let talentCap = TalentEngine.talentCap(inst)
        let atkLayers = inst.talents[TalentBranch.attack.rawValue] ?? 0
        let defLayers = inst.talents[TalentBranch.defense.rawValue] ?? 0
        let supLayers = inst.talents[TalentBranch.support.rawValue] ?? 0
        let talentAlloc = TalentEngine.allocated(inst)
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(def?.name ?? inst.defId).bold()
                Spacer()
                Text("Lv.\(inst.level)/\(cap)\(inst.breakthrough > 0 ? " 突破\(inst.breakthrough)" : "")\(inst.tribulations > 0 ? " 渡劫\(inst.tribulations)" : "")").foregroundStyle(.red).bold()
            }
            if let e = def?.element, let r = def?.rarity {
                Text("\(e.label)行 · \(r.label)级\(inst.tribulations > 0 ? " · 属性×(1+25%×\(inst.tribulations))" : "")").font(.caption).foregroundStyle(.secondary)
            }
            ProgressView(value: ratio)
            Text(inst.level >= cap ? "已达等级上限（可突破）" : "经验 \(inst.exp)/\(need)").font(.caption2).foregroundStyle(.secondary)
            HStack {
                Button("修炼 +60") { onTrain() }
                Button(canBreak ? "突破(妖丹\(breakCost))" : "未达突破") { onBreak() }
                    .disabled(!(canBreak && yaodan >= breakCost))
                Button(canEvolve ? "进化" : "暂不可进化") { onEvolve() }
                    .disabled(!canEvolve)
            }
            let tribLabel = inst.tribulations >= maxTrib ? "已渡劫至巅（神阶·不可再渡）"
                : (canTrib ? "渡劫飞升(妖丹\(tribCost))" : "未达渡劫：需满级满突破")
            Button(tribLabel) { onTribulate() }
                .disabled(!(canTrib && yaodan >= tribCost))
                .frame(maxWidth: .infinity)
            Text("天赋树（攻/防/辅）  已点 \(talentAlloc)/\(talentCap)").bold().font(.subheadline)
            HStack {
                Button("攻 Lv.\(atkLayers)") { onTalent(.attack) }
                    .disabled(!TalentEngine.canAssign(inst, .attack))
                Button("防 Lv.\(defLayers)") { onTalent(.defense) }
                    .disabled(!TalentEngine.canAssign(inst, .defense))
                Button("辅 Lv.\(supLayers)") { onTalent(.support) }
                    .disabled(!TalentEngine.canAssign(inst, .support))
            }
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
    }
}

// MARK: - 天劫演出 overlay（渡劫飞升）
/// 全屏天劫闪电闪烁 + 稀有度升阶揭示；2.6s 自动关闭，亦可点击关闭。
/// ⚠️ 纯视觉：本机无 JDK 无法编译验证，需在 Xcode 中构建确认。
struct TribulationOverlay: View {
    let name: String
    let times: Int
    let onDone: () -> Void
    @State private var flash = false
    var body: some View {
        ZStack {
            Color.black.opacity(0.82).ignoresSafeArea()
            Color.white.opacity(flash ? 0.5 : 0.0).ignoresSafeArea()
            VStack(spacing: 10) {
                Text("⚡ 天劫降临 ⚡").font(.title).bold().foregroundStyle(.white)
                Text(name).font(.largeTitle).bold().foregroundStyle(Color(red: 1, green: 0.84, blue: 0))
                Text("渡劫飞升！第 \(times) 次").font(.title2).foregroundStyle(.white)
                Text("（点击任意处关闭）").font(.caption2).foregroundStyle(.white.opacity(0.6))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.12).repeatForever(autoreverses: true)) { flash = true }
            Task { try? await Task.sleep(nanoseconds: 2_600_000_000); onDone() }
        }
        .onTapGesture { onDone() }
    }
}
