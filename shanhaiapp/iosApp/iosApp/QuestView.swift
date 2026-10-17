import SwiftUI

// MARK: - 任务 / 成就
struct QuestView: View {
    @EnvironmentObject var game: GameState
    @State private var selected: QuestCategory = .daily

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(QuestCategory.allCases, id: \.self) { cat in
                            let col = questCategoryColor(cat)
                            Button {
                                selected = cat
                            } label: {
                                Text(questCategoryLabel(cat))
                                    .font(.subheadline).bold()
                                    .foregroundStyle(selected == cat ? .white : col)
                                    .padding(.horizontal, 14).padding(.vertical, 8)
                                    .background(
                                        RoundedRectangle(cornerRadius: 20)
                                            .fill(selected == cat ? col : col.opacity(0.15))
                                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(col.opacity(0.5), lineWidth: 1))
                                    )
                            }
                        }
                    }
                    .padding(12)
                }

                HStack(spacing: 8) {
                    SummaryChip(label: "进行中", value: "\(game.quests.activeCount)", color: .brown)
                    SummaryChip(label: "可领取", value: "\(game.quests.completableCount)", color: .yellow)
                }
                .padding(.horizontal, 16)

                let list = game.quests.byCategory(selected)
                ScrollView {
                    VStack(spacing: 8) {
                        if list.isEmpty {
                            Text("该分类暂无任务").font(.subheadline).foregroundStyle(.secondary)
                                .padding()
                        }
                        ForEach(list) { q in
                            QuestCardView(q: q) {
                                _ = game.claimQuest(id: q.def.id)
                                game.save() // 领取后落盘，保留 CLAIMED 状态
                            }
                        }
                        Spacer(minLength: 8)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 8)
                }
            }
            .navigationTitle("任务 · 进程记录")
            .id(game.questVersion)  // 任务进度变化时刷新
        }
    }
}

struct SummaryChip: View {
    let label: String; let value: String; let color: Color
    var body: some View {
        VStack {
            Text(value).font(.title).bold().foregroundStyle(color)
            Text(label).font(.caption)
        }
        .frame(maxWidth: .infinity)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
    }
}

struct QuestCardView: View {
    let q: QuestProgress
    let onClaim: () -> Void
    var body: some View {
        let col = questCategoryColor(q.def.category)
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(q.def.title).font(.headline)
                Spacer()
                Text(q.state == .completed ? "可领取" : q.state == .claimed ? "已领取" : "进行中")
                    .font(.caption).bold()
                    .foregroundStyle(q.state == .completed ? .yellow : q.state == .claimed ? .secondary : col)
            }
            Text(q.def.desc).font(.caption).foregroundStyle(.secondary)

            ForEach(q.def.objectives.indices, id: \.self) { i in
                VStack(alignment: .leading, spacing: 2) {
                    ProgressView(value: Double(q.ratio(i)), total: 1.0).tint(col)
                    Text(q.objectiveText(i)).font(.caption2)
                }
            }

            HStack {
                Text("奖励：\(q.def.reward.text)").font(.caption).bold().foregroundStyle(.purple)
                Spacer()
                if q.state == .completed {
                    Button("领取") { onClaim() }.buttonStyle(.borderedProminent).controlSize(.small)
                } else if q.state == .claimed {
                    Text("✓ 已领取").font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
    }
}
