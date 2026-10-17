import SwiftUI

// MARK: - 对战（2.5D 风格 + 克制技高亮 + 自动战斗）
struct BattleView: View {
    @EnvironmentObject var game: GameState
    @State private var session = BattleSession(playerTeam: [], enemyTeam: [])
    @State private var built = false
    @State private var auto = false
    @State private var tick = 0
    @State private var timer: Timer?
    @State private var recordedWin = false
    @State private var selWeather: Weather = .clear       // 天气地形战斗（GDD 十九）
    @State private var selTerrain: RegionTheme? = nil

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Text("2.5D 对战").font(.title2).bold()
                if built {
                    battleContent.id(tick)
                }
            }
            .padding()
            .navigationTitle("对战")
            .onAppear { if !built { rebuild(); built = true } }
            .onDisappear { timer?.invalidate() }
        }
    }

    func rebuild() {
        timer?.invalidate(); auto = false; tick = 0; recordedWin = false
        let insts = game.teamMonsters()
        let players: [Combatant]
        if insts.isEmpty {
            let starter = MonsterPool.byId("yao_she")!
            players = [Combatant(monster: starter, level: 10, equipped: Array(starter.skills.prefix(4)), breakthrough: 0)]
        } else {
            players = insts.map { game.buildCombatant($0) }
        }
        var a = MonsterPool.randomEncounter(); var b = MonsterPool.randomEncounter(); var g = 0
        while b.id == a.id && g < 10 { b = MonsterPool.randomEncounter(); g += 1 }
        let enemies = [Combatant(monster: a, level: 8, equipped: Array(a.skills.prefix(4)), breakthrough: 0),
                       Combatant(monster: b, level: 8, equipped: Array(b.skills.prefix(4)), breakthrough: 0)]
        // 阵容羁绊 × 阵法风水增益（缝合 #85）作为全队攻击增伤；天气/地形作为环境乘子传入
        selWeather = game.currentWeather; selTerrain = game.currentTerrain
        let bias = game.currentTerrainBias()
        session = BattleSession(playerTeam: players, enemyTeam: enemies,
                                playerTeamMul: game.battleTeamMul(), weather: selWeather, terrainBias: bias,
                                playerSectAttackMul: game.sectAttackMul())
    }

    /// 应用天气/地形选择并重开战斗
    func applyEnv() {
        game.setWeather(selWeather); game.setTerrain(selTerrain)
        rebuild()
    }

    @ViewBuilder
    var battleContent: some View {
        let enemy = session.currentEnemy()
        let player = session.currentPlayer()
        let planned = BattleEngine.bestCounterSkill(attacker: player, defender: enemy)
        // 阵容羁绊（GDD 七·队伍组合技）
        let synergies = game.teamSynergies()
        let mul = game.teamAttackMultiplier()
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("阵容羁绊").bold()
                Spacer()
                Text("全队攻击 ×\(String(format: "%.2f", mul))").font(.caption).foregroundStyle(.purple)
            }
            if synergies.isEmpty {
                Text("无激活羁绊（调整出战阵容以触发组合技）").font(.caption).foregroundStyle(.secondary)
            } else {
                ForEach(synergies, id: \.id) { s in
                    Text("· \(s.name)：增伤 +\(Int(s.atkMul * 100))%、大招 \(s.ultimateCharges) 次 —— \(s.desc)")
                        .font(.caption)
                }
            }
        }
        .padding(10).frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

        // 天气地形战斗（GDD 十九·长线留存）：天气/地形临时偏置五行攻击
        VStack(alignment: .leading, spacing: 4) {
            Text("天气地形（GDD 十九）").bold()
            Text("天气").font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 6) {
                ForEach(Weather.allCases, id: \.self) { w in
                    Button(action: { selWeather = w }) {
                        Text(weatherCN(w)).padding(.horizontal, 6).padding(.vertical, 4)
                            .background(selWeather == w ? Color.red.opacity(0.15) : Color.clear)
                            .cornerRadius(8)
                    }.buttonStyle(.plain)
                }
            }
            Text("地形").font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 6) {
                ForEach([RegionTheme?]([nil, .shanlin, .shuiyu, .gumu, .donghai]), id: \.self) { t in
                    Button(action: { selTerrain = t }) {
                        Text(terrainCN(t)).padding(.horizontal, 4).padding(.vertical, 4)
                            .background(selTerrain == t ? Color.red.opacity(0.15) : Color.clear)
                            .cornerRadius(8)
                    }.buttonStyle(.plain)
                }
            }
            if let lead = session.playerTeam.first?.monster.element {
                let wm = WeatherEngine.weatherAttackMul(lead, selWeather)
                let tb = (game.currentTerrainBias().map { WeatherEngine.terrainAttackMul(lead, $0) } ?? 1.0)
                Text("首发(\(lead.label)行) 预览：天气 ×\(String(format: "%.2f", wm))" + (tb != 1.0 ? " · 地形 ×\(String(format: "%.2f", tb))" : ""))
                    .font(.caption)
            }
            Button("应用环境并重开") { applyEnv() }.buttonStyle(.bordered).frame(maxWidth: .infinity)
        }
        .padding(10).frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

        // 连携组合技（GDD 七·连携）：展示首发妖出战 4 技能可触发的连携
        let leadCombos = ComboEngine.availableCombos(session.playerTeam.first?.monster.skills.prefix(4).map { $0.id } ?? [])
        VStack(alignment: .leading, spacing: 4) {
            Text("连携组合技").bold()
            if leadCombos.isEmpty {
                Text("首发妖无连携（同妖装备特定技能对可触发）").font(.caption).foregroundStyle(.secondary)
            } else {
                ForEach(leadCombos, id: \.id) { c in
                    let eff = c.effect.kind == .damageMul ? "伤害 ×\(String(format: "%.2f", c.effect.value))"
                               : (c.effect.kind == .healPercent ? "回血 +\(Int(c.effect.value * 100))%" : "控制")
                    Text("· \(c.name)：\(eff)（CD \(c.cd)）").font(.caption)
                }
            }
        }
        .padding(10).frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

        Text("敌方剩余 \(session.enemyTeam.filter { $0.isAlive }.count) · 我方剩余 \(session.playerTeam.filter { $0.isAlive }.count)")
            .font(.caption).foregroundStyle(.secondary)

        MonsterPanel(c: enemy, isEnemy: true, big: true)

        if session.outcome == .ongoing, let p = planned {
            VStack(spacing: 4) {
                Text("推荐克制技：\(p.name)").bold().foregroundStyle(.red)
                Text(Element.multiplierLabel(attacker: p.element, defender: enemy.monster.element))
            }
            .padding(8).frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
        }

        if let last = session.log.last {
            var s = "\(last.attackerName) → \(last.skillName) \(last.damage)"
            if last.crit { s += " 暴击!" }
            if last.envMul != 1.0 { s += " 环境×\(String(format: "%.2f", last.envMul))" }
            Text(s).font(.caption).foregroundStyle(last.side == .player ? .primary : .red)
        }

        MonsterPanel(c: player, isEnemy: false, big: false)

        ScrollView {
            VStack(alignment: .leading, spacing: 2) {
                ForEach(Array(session.log.suffix(6).reversed().enumerated()), id: \.offset) { _, ev in
                    var s = "\(ev.attackerName) → \(ev.defenderName)：\(ev.skillName) \(ev.damage)"
                    if ev.crit { s += " 暴击" }
                    if ev.envMul != 1.0 { s += " 环境×\(String(format: "%.2f", ev.envMul))" }
                    Text(s).font(.caption2).foregroundStyle(ev.side == .player ? .primary : .red)
                }
                if session.log.isEmpty { Text("点击「出招」开始对战").font(.caption2).foregroundStyle(.secondary) }
            }
        }
        .frame(maxHeight: 120)
        .padding(8).background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

        if session.outcome == .ongoing {
            HStack {
                Button("出招") { stepOnce() }.buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
                Toggle("自动", isOn: $auto).onChange(of: auto) { _ in toggleAuto() }
            }
        } else {
            Text(session.outcome == .playerWin ? "胜利！妖力大涨" : "败北……再修炼一番")
                .font(.title3).bold().foregroundStyle(session.outcome == .playerWin ? .primary : .red)
            Button("再战") { rebuild() }.buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
        }
    }

    func stepOnce() { advance() }
    func advance() {
        session.step(); tick += 1
        if session.outcome != .ongoing { game.completeTutorialStep(3) } // 引导步 ③：首战
        if session.outcome == .playerWin && !recordedWin {
            recordedWin = true
            game.onBattleWon(element: session.currentPlayer().monster.element)
            game.save() // 胜利落盘
        }
    }
    func toggleAuto() {
        timer?.invalidate()
        if auto {
            timer = Timer.scheduledTimer(withTimeInterval: 0.9, repeats: true) { t in
                if session.outcome == .ongoing { advance() }
                else { t.invalidate(); auto = false }
            }
        }
    }
}

struct MonsterPanel: View {
    let c: Combatant
    let isEnemy: Bool
    let big: Bool
    var body: some View {
        let col = elementColor(c.monster.element)
        HStack(spacing: 12) {
            Circle().fill(col.opacity(0.2))
                .frame(width: big ? 64 : 52, height: big ? 64 : 52)
                .overlay(Text(String(c.monster.name.first ?? "?")).font(big ? .title : .title2).bold().foregroundStyle(col))
                .overlay(Circle().stroke(col, lineWidth: 2))
            VStack(alignment: .leading) {
                Text("\(isEnemy ? "敌方" : "我方") · \(c.monster.name)").bold()
                Text("Lv.\(c.level) · \(c.monster.element.label)行 · \(c.monster.rarity.label)级").font(.caption).foregroundStyle(.secondary)
                ProgressView(value: max(0, Double(c.hp)), total: Double(max(1, c.maxHp))).tint(c.hp > c.maxHp / 3 ? col : .red)
                Text("HP \(max(0, c.hp)) / \(c.maxHp)").font(.caption2)
            }
            Spacer()
        }
        .padding(12).frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
    }
}

private func weatherCN(_ w: Weather) -> String {
    switch w { case .clear: return "晴"; case .rain: return "雨"; case .snow: return "雪"; case .windy: return "风" }
}
private func terrainCN(_ t: RegionTheme?) -> String {
    guard let t = t else { return "无" }
    switch t { case .shanlin: return "山林"; case .shuiyu: return "水域"; case .gumu: return "古墓"; case .donghai: return "东海" }
}
