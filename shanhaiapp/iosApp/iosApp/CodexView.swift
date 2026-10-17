import SwiftUI

// MARK: - 图鉴
struct CodexView: View {
    @EnvironmentObject var game: GameState
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("妖怪图鉴").font(.title2).bold()
                    let comp = game.codex.regionCompletion()
                    VStack(alignment: .leading, spacing: 6) {
                        Text("地域图鉴完成度 \(Int(comp * 100))%  （\(game.codex.regionCaught)/\(game.codex.all.count)）").bold()
                        ProgressView(value: comp)
                        Text("秘境收的妖不计入地域图鉴").font(.caption2).foregroundStyle(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))

                    ForEach(MonsterCategory.allCases, id: \.self) { cat in
                        Text("\(cat.label)类").font(.headline)
                        let entries = game.codex.byCategory(cat)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 100))], spacing: 8) {
                            ForEach(entries) { e in
                                MonsterChip(monster: e.monster, caught: e.caught)
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("图鉴")
        }
    }
}

struct MonsterChip: View {
    let monster: Monster
    let caught: Bool
    var body: some View {
        VStack {
            Circle()
                .fill(caught ? elementColor(monster.element).opacity(0.2) : Color.gray.opacity(0.2))
                .frame(width: 56, height: 56)
                .overlay(Text(caught ? String(monster.name.first ?? "?") : "?").font(.title).bold().foregroundStyle(caught ? elementColor(monster.element) : .gray))
                .overlay(Circle().stroke(elementColor(monster.element), lineWidth: 2))
            Text(caught ? monster.name : "未收录").font(.caption)
            HStack(spacing: 4) {
                Tag(text: monster.element.label, color: elementColor(monster.element))
                Tag(text: monster.rarity.label, color: rarityColor(monster.rarity))
            }
        }
        .padding(6)
        .background(RoundedRectangle(cornerRadius: 12).fill(caught ? Color(.systemBackground) : Color.gray.opacity(0.1)))
    }
}

struct Tag: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text).font(.caption2).bold().foregroundStyle(color)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 6).fill(color.opacity(0.18)))
    }
}
