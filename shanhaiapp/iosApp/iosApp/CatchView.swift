import SwiftUI

// MARK: - 收妖（收缩光环点按）
struct CatchView: View {
    let monster: Monster
    let fromSecret: Bool
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var game: GameState

    @State private var phase = 0          // 0 出现, 1 光环, 2 结果
    @State private var assist = true
    @State private var result: CaptureResult?
    @State private var ringRadius: CGFloat = 150
    @State private var timer: Timer?
    @State private var questsDone: [String] = []

    let maxR: CGFloat = 150
    let minR: CGFloat = 34

    var body: some View {
        VStack {
            HStack {
                Button { dismiss() } label: { Image(systemName: "chevron.left"); Text("返回") }
                Spacer()
                Toggle("辅助", isOn: $assist)
            }
            .padding()

            Spacer()

            ZStack {
                Circle()
                    .stroke(style: StrokeStyle(lineWidth: 4, dash: [10, 10]))
                    .foregroundStyle(.white.opacity(0.5))
                    .frame(width: minR * 2, height: minR * 2)
                Circle()
                    .stroke(elementColor(monster.element), lineWidth: 8)
                    .frame(width: ringRadius * 2, height: ringRadius * 2)
                Circle()
                    .fill(elementColor(monster.element).opacity(0.2))
                    .frame(width: 96, height: 96)
                    .overlay(Text(String(monster.name.first ?? "?")).font(.largeTitle).bold().foregroundStyle(elementColor(monster.element)))
                    .onTapGesture { if phase == 0 { startRing() } }
            }
            .frame(width: 340, height: 340)
            .contentShape(Rectangle())
            .onTapGesture { if phase == 1 { captureTap() } }

            Text(monster.name).font(.title2).bold()
            Text("\(monster.element.label)行 · \(monster.rarity.label)级").font(.subheadline).foregroundStyle(.secondary)

            Spacer()

            if phase == 0 {
                Button("收妖") { startRing() }.buttonStyle(.borderedProminent)
            } else if phase == 1 {
                Text("光环缩到最小再点！").foregroundStyle(elementColor(monster.element)).bold()
                Text("（点击光环区域收妖）").font(.caption).foregroundStyle(.secondary)
            } else if phase == 2, let r = result {
                if r.success {
                    Text("收服成功！").font(.title).bold().foregroundStyle(.primary)
                    Text("命中 \((r.accuracy * 100).rounded())% · 成功率 \((r.probability * 100).rounded())%")
                    if !questsDone.isEmpty {
                        Text("任务完成：\(questsDone.joined(separator: "、"))")
                            .font(.subheadline).bold().foregroundStyle(.purple)
                    }
                    Button("去图鉴 ›") { dismiss() }.buttonStyle(.borderedProminent)
                } else {
                    Text("妖挣脱了！").font(.title).bold().foregroundStyle(.red)
                    Text("命中 \((r.accuracy * 100).rounded())% · 成功率 \((r.probability * 100).rounded())%")
                    Button("再试一次") { startRing() }.buttonStyle(.bordered)
                }
            }
            Spacer()
        }
        .padding()
        .background(Color(.systemBackground))
    }

    func startRing() {
        phase = 1
        result = nil
        questsDone = []
        ringRadius = maxR
        let total = Double(CaptureEngine.ringCycleMs(rarity: monster.rarity, assist: assist))
        let start = Date()
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0 / 60, repeats: true) { t in
            let elapsed = Date().timeIntervalSince(start) * 1000
            let frac = min(1.0, elapsed / total)
            ringRadius = maxR + (minR - maxR) * CGFloat(frac)
            if frac >= 1 { t.invalidate() }
        }
    }

    func captureTap() {
        timer?.invalidate()
        let acc = CaptureEngine.accuracy(tapRadius: Float(ringRadius), minRadius: Float(minR), maxRadius: Float(maxR), assist: assist)
        let res = CaptureEngine.resolve(accuracy: acc, rarity: monster.rarity, assist: assist, captureMul: game.sectCaptureMul() * game.activityCaptureMul())
        result = res
        phase = 2
        if res.success { questsDone = game.catchMonster(id: monster.id, secret: fromSecret); game.save() }
    }
}
