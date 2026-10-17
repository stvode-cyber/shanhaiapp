import SwiftUI

func elementColor(_ e: Element) -> Color {
    switch e {
    case .metal: return Color(red: 0.902, green: 0.706, blue: 0.133)
    case .wood: return Color(red: 0.298, green: 0.686, blue: 0.314)
    case .earth: return Color(red: 0.631, green: 0.533, blue: 0.498)
    case .water: return Color(red: 0.129, green: 0.588, blue: 0.953)
    case .fire: return Color(red: 0.898, green: 0.224, blue: 0.208)
    case .wind: return Color(red: 0.149, green: 0.651, blue: 0.604)
    case .thunder: return Color(red: 0.494, green: 0.341, blue: 0.761)
    }
}

func rarityColor(_ r: Rarity) -> Color {
    switch r {
    case .fan: return .gray
    case .ling: return .blue
    case .xian: return Color(red: 0.671, green: 0.278, blue: 0.737)
    case .shen: return Color(red: 1.0, green: 0.702, blue: 0.0)
    }
}

func categoryColor(_ c: MonsterCategory) -> Color {
    switch c {
    case .jing: return .green
    case .guai: return .red
    case .yao: return .pink
    case .lingshou: return .yellow
    }
}

func questCategoryColor(_ c: QuestCategory) -> Color {
    switch c {
    case .daily: return Color(red: 0.553, green: 0.431, blue: 0.384)   // 褐
    case .story: return Color(red: 0.361, green: 0.420, blue: 0.753)   // 靛
    case .trial: return Color(red: 0.149, green: 0.651, blue: 0.604)   // 青
    case .achievement: return Color(red: 1.0, green: 0.702, blue: 0.0) // 金
    case .event: return Color(red: 0.925, green: 0.251, blue: 0.478)   // 桃红
    }
}

func questCategoryLabel(_ c: QuestCategory) -> String {
    switch c {
    case .daily: return "今日目标"
    case .story: return "山海异闻"
    case .trial: return "灵脉试炼"
    case .achievement: return "成就"
    case .event: return "年节限时"
    }
}
