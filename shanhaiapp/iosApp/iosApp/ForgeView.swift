import SwiftUI

// MARK: - 炼妖（装备祭炼 + 炼妖融合）
struct ForgeView: View {
    @EnvironmentObject var game: GameState
    @State private var tick = 0
    @State private var selected: String? = nil
    @State private var fuseA: String? = nil
    @State private var fuseB: String? = nil
    @State private var fuseMsg: String? = nil
    @State private var showingInherit = false
    @State private var inheritTarget: String? = nil
    @State private var forgeFx: (type: String, name: String)? = nil

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("炼妖 · 装备祭炼").font(.title2).bold()
                    Text("妖丹 \(game.yaodan)（祭炼/融合消耗） · 装备 \(game.equipment.count) 件")
                        .font(.subheadline).foregroundStyle(.secondary)

                    if game.owned.isEmpty {
                        Text("还没有妖宠，先去「探索」收服。").padding()
                    } else {
                        // 选择妖
                        Text("选择妖宠").font(.headline)
                        HStack(spacing: 8) {
                            ForEach(Array(game.owned.values), id: \.instanceId) { inst in
                                let name = MonsterPool.byId(inst.defId)?.name ?? inst.defId
                                let isSel = selected == inst.instanceId
                                Button {
                                    withAnimation { selected = (isSel ? nil : inst.instanceId) }
                                } label: {
                                    Text(isSel ? "▣ \(name)" : name)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(RoundedRectangle(cornerRadius: 10).fill(isSel ? Color.red.opacity(0.15) : Color(.secondarySystemBackground)))
                                        .foregroundStyle(isSel ? .red : .primary)
                                }
                            }
                        }
                        if let sid = selected, let inst = game.owned[sid] {
                            MonsterEquipRow(inst: inst)
                        }

                        // 装备背包
                        Text("装备背包").font(.headline)
                        ForEach(game.equipment, id: \.instanceId) { oe in
                            let ed = EquipmentLibrary.byId(oe.equipId)
                            let onInst = game.equipped.first(where: { $0.value.values.contains(oe.instanceId) })?.key
                            let onName = onInst.flatMap { game.owned[$0]?.defId }.flatMap { MonsterPool.byId($0)?.name }
                            let bonus = ed.map { EquipmentEngine.bonusAtLevel($0, oe.level, false) }
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(ed?.name ?? oe.equipId).bold()
                                    Spacer()
                                    Text(slotLabel(ed?.slot) + " · Lv.\(oe.level)\(ed.map { "/\($0.maxLevel)" } ?? "")")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                if let b = bonus {
                                    Text("加成 攻\(b.atk) 防\(b.def) 血\(b.hp) 速\(b.spd)")
                                        .font(.caption2).foregroundStyle(.secondary)
                                }
                                if let onName { Text("已装备于 \(onName)").font(.caption2).foregroundStyle(.red) }
                                if oe.stars > 1 || oe.tier > 1 || !oe.substats.isEmpty {
                                    Text("★\(oe.stars)/\(EquipmentEngine.MAX_STAR) 阶\(oe.tier)/\(ed.map { EquipmentEngine.maxTier($0.rarity) } ?? 1)\(oe.substats.map { " \(subLabel($0.stat))+\($0.value)" }.joined())")
                                        .font(.caption2).foregroundStyle(.secondary)
                                }
                                HStack {
                                    Button {
                                        if let sid = selected { _ = game.equipItem(instanceId: sid, oe.instanceId); game.save(); tick += 1 }
                                    } label: { Text("装备") }
                                    .disabled(selected == nil || onInst != nil)
                                    Button {
                                        _ = game.forgeEquip(oe.instanceId); game.save(); tick += 1
                                    } label: { Text(EquipmentEngine.canForge(oe) ? "祭炼(-\(EquipmentEngine.FORGE_COST_YAODAN)妖丹)" : "已满级") }
                                    .disabled(!(EquipmentEngine.canForge(oe) && game.yaodan >= EquipmentEngine.FORGE_COST_YAODAN))
                                }
                                HStack {
                                    Button { _ = game.starUpEquip(oe.instanceId); forgeFx = (type: "升星", name: ed?.name ?? oe.equipId); game.save(); tick += 1 }
                                        label: { Text(EquipmentEngine.canStarUp(oe) ? "升星(-\(EquipmentEngine.starCost(oe))玉)" : "满星") }
                                        .disabled(!(EquipmentEngine.canStarUp(oe) && game.lingyu >= EquipmentEngine.starCost(oe)))
                                    Button { _ = game.breakthroughEquip(oe.instanceId); forgeFx = (type: "突破", name: ed?.name ?? oe.equipId); game.save(); tick += 1 }
                                        label: { Text((ed.map { EquipmentEngine.canBreakthrough(oe, $0) } ?? false) ? "突破(-\(EquipmentEngine.tierCost(oe))玉)" : "满阶") }
                                        .disabled(!((ed.map { EquipmentEngine.canBreakthrough(oe, $0) } ?? false) && game.lingyu >= EquipmentEngine.tierCost(oe)))
                                    Button { _ = game.temperEquip(oe.instanceId); forgeFx = (type: "淬炼", name: ed?.name ?? oe.equipId); game.save(); tick += 1 }
                                        label: { Text(EquipmentEngine.canTemper(oe) ? "淬炼(-\(EquipmentEngine.temperCost(oe))玉)" : "满淬") }
                                        .disabled(!(EquipmentEngine.canTemper(oe) && game.lingyu >= EquipmentEngine.temperCost(oe)))
                                }
                                Button { inheritTarget = oe.instanceId; showingInherit = true }
                                    label: { Text("继承（选源）").frame(maxWidth: .infinity) }
                                    .buttonStyle(.bordered)
                            }
                            .padding().background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                        }

                        // 炼妖融合
                        Text("炼妖融合").font(.headline)
                        Text("选两只妖熔炼为更强新妖（消耗妖丹 \(FusionEngine.FUSE_COST_YAODAN)）")
                            .font(.caption).foregroundStyle(.secondary)
                        HStack(spacing: 8) {
                            Button { fuseA = pickFuse(game.owned.values.map { $0.instanceId }, fuseA, fuseB) } label: { Text(fuseA.map(nameOf) ?? "选妖 A").frame(maxWidth: .infinity) }
                                .buttonStyle(.bordered)
                            Button { fuseB = pickFuse(game.owned.values.map { $0.instanceId }, fuseB, fuseA) } label: { Text(fuseB.map(nameOf) ?? "选妖 B").frame(maxWidth: .infinity) }
                                .buttonStyle(.bordered)
                        }
                        Button {
                            guard let a = fuseA, let b = fuseB else { return }
                            if let r = game.fuse(a, b) {
                                fuseMsg = "融合成功！获得 \(MonsterPool.byId(r.newDefId)?.name ?? r.newDefId) Lv.\(r.newLevel)"
                                fuseA = nil; fuseB = nil
                            } else {
                                fuseMsg = "融合失败：需两只不同妖且妖丹充足"
                            }
                            game.save(); tick += 1
                        } label: { Text("融合").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent)
                        .disabled(fuseA == nil || fuseB == nil || fuseA == fuseB || game.yaodan < FusionEngine.FUSE_COST_YAODAN)
                        if let m = fuseMsg { Text(m).font(.subheadline).bold().foregroundStyle(.red) }
                    }
                }
                .padding().id(tick)
            }
            .navigationTitle("炼妖")
        }
        .sheet(isPresented: $showingInherit) {
            if let target = inheritTarget { InheritSheet(target: target) }
        }
        .overlay {
            if let fx = forgeFx {
                ForgeFxOverlay(type: fx.type, name: fx.name) { forgeFx = nil }
            }
        }
    }

    private func slotLabel(_ s: EquipSlot?) -> String {
        guard let s else { return "" }
        switch s { case .weapon: return "武器"; case .armor: return "护甲"; case .accessory: return "饰品" }
    }
    private func nameOf(_ instId: String) -> String {
        (game.owned[instId]?.defId).flatMap { MonsterPool.byId($0)?.name } ?? instId
    }
    private func pickFuse(_ all: [String], _ cur: String?, _ other: String?) -> String? {
        let cands = all.filter { $0 != other }
        guard !cands.isEmpty else { return nil }
        if let cur, let i = cands.firstIndex(of: cur) { return cands[(i + 1) % cands.count] }
        return cands.first
    }
}

struct MonsterEquipRow: View {
    @EnvironmentObject var game: GameState
    let inst: MonsterInstance
    var body: some View {
        let eff = game.effectiveStatsOf(inst)
        VStack(alignment: .leading, spacing: 6) {
            Text(MonsterPool.byId(inst.defId)?.name ?? inst.defId).bold()
            Text("有效属性  攻 \(eff.atk) · 防 \(eff.def) · 血 \(eff.hp) · 速 \(eff.spd)")
                .font(.caption)
            ForEach(EquipSlot.allCases, id: \.self) { slot in
                let eid = game.equipped[inst.instanceId]?[slot]
                let oe = eid.flatMap { game.findEquip($0) }
                let ed = oe.flatMap { EquipmentLibrary.byId($0.equipId) }
                HStack {
                    if let oe, let ed {
                        Text("\(slotLabel(ed.slot))：\(ed.name) Lv.\(oe.level)").font(.subheadline)
                        Spacer()
                        Button("祭炼") { _ = game.forgeEquip(oe.instanceId); game.save() }
                        Button("卸下") { game.unequipItem(instanceId: inst.instanceId, slot); game.save() }
                    } else {
                        Text("\(slotLabel(slot))：空").font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding().background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
    }
    private func slotLabel(_ s: EquipSlot) -> String {
        switch s { case .weapon: return "武器"; case .armor: return "护甲"; case .accessory: return "饰品" }
    }
    private func subLabel(_ s: EquipStat) -> String {
        switch s { case .atk: return "攻"; case .def: return "防"; case .hp: return "血"; case .spd: return "速" }
    }
}

// MARK: - 继承选源 Sheet
struct InheritSheet: View {
    @EnvironmentObject var game: GameState
    let target: String
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        let tSlot = game.findEquip(target).flatMap { EquipmentLibrary.byId($0.equipId)?.slot }
        NavigationStack {
            List {
                ForEach(game.equipment.filter { $0.instanceId != target && EquipmentLibrary.byId($0.equipId)?.slot == tSlot }, id: \.instanceId) { src in
                    Button {
                        _ = game.inheritEquip(src.instanceId, target)
                        game.save(); dismiss()
                    } label: { Text("\(EquipmentLibrary.byId(src.equipId)?.name ?? src.equipId) ★\(src.stars) 阶\(src.tier)") }
                }
            }
            .navigationTitle("选择继承源（同部位）")
        }
    }
}

// MARK: - 装备养成演出 overlay（升星 / 突破 / 淬炼）
/// 全屏星辉脉冲 + 成功文案；1.3s 自动关闭，亦可点击关闭。
/// ⚠️ 纯视觉：本机无 JDK 无法编译验证，需在 Xcode 中构建确认。
struct ForgeFxOverlay: View {
    let type: String
    let name: String
    let onDone: () -> Void
    @State private var scale: CGFloat = 0.8
    var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(spacing: 6) {
                Text("✦ \(type)成功 ✦").font(.title2).bold().foregroundStyle(Color(red: 1, green: 0.84, blue: 0))
                Text(name).font(.title3).foregroundStyle(.white)
            }
            .scaleEffect(scale)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true)) { scale = 1.15 }
            Task { try? await Task.sleep(nanoseconds: 1_300_000_000); onDone() }
        }
        .onTapGesture { onDone() }
    }
}
