import SwiftUI
import AbetaSduiKit
import AbetaWatchKit

// MARK: - 1. iOS & iPadOS SDUI Host (`AbetaSduiKit.xcframework`)

struct AbetaIosSduiScreenView: UIViewControllerRepresentable {
    let screenId: String
    let bffBaseUrl: String?

    init(screenId: String = "store-home", bffBaseUrl: String? = nil) {
        self.screenId = screenId
        self.bffBaseUrl = bffBaseUrl
    }

    func makeUIViewController(context: Context) -> UIViewController {
        SduiViewControllerKt.createSduiViewController(
            screenId: screenId,
            bffBaseUrl: bffBaseUrl
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct AbetaIosRootAppView: View {
    @State private var activeScreenId: String
    private let bffBaseUrl: String?

    init(initialScreenId: String = "store-home", bffBaseUrl: String? = nil) {
        _activeScreenId = State(initialValue: initialScreenId)
        self.bffBaseUrl = bffBaseUrl
    }

    var body: some View {
        NavigationStack {
            AbetaIosSduiScreenView(
                screenId: activeScreenId,
                bffBaseUrl: bffBaseUrl
            )
            .id(activeScreenId)
            .ignoresSafeArea(.container, edges: .all)
            .toolbar(.hidden, for: .navigationBar)
        }
        .onOpenURL { url in
            if let resolved = resolveScreenId(from: url) {
                activeScreenId = resolved
            }
        }
    }

    private func resolveScreenId(from url: URL) -> String? {
        let components = url.pathComponents.filter { $0 != "/" }
        if let screensIdx = components.firstIndex(of: "screens"),
           components.indices.contains(screensIdx + 1) {
            return components[screensIdx + 1]
        }
        return components.last ?? url.host
    }
}

// MARK: - 2. Apple Watch (`AbetaWatchKit.xcframework`)

@MainActor
final class AbetaWatchViewModel: ObservableObject {
    private let holder: WatchCompanionHostHolder
    private var observeTask: Task<Void, Never>?

    @Published var metrics: [WatchMetricGlance] = []
    @Published var cards: [WatchActionCard] = []
    @Published var titleText: String = "Abeta Watch"
    @Published var isOffline: Bool = false
    @Published var isLoading: Bool = true
    @Published var errorMessage: String? = nil
    @Published var toastMessage: String? = nil

    init(bffBaseUrl: String? = nil, initialScreenId: String = "watch-status") {
        self.holder = WatchAppleFactoryKt.createAppleWatchCompanionHost(bffBaseUrl: bffBaseUrl)
        holder.controller.loadScreen(screenId: initialScreenId)
        startObservingState()
    }

    deinit {
        observeTask?.cancel()
        holder.close()
    }

    func refresh() {
        isLoading = true
        errorMessage = nil
        holder.controller.refresh()
        syncFromControllerState()
    }

    func trigger(_ action: ActionRef?) {
        guard let action else { return }
        holder.controller.onAction(action: action)
        syncFromControllerState()
    }

    func dismissToast() {
        holder.controller.dismissToast()
        toastMessage = nil
    }

    func syncFromControllerState() {
        let rawState = holder.controller.uiState.value
        switch rawState {
        case is WatchUiStateIdle, is WatchUiStateLoading:
            isLoading = true
            errorMessage = nil

        case let ready as WatchUiStateReady:
            isLoading = false
            errorMessage = nil
            titleText = ready.page.title
            isOffline = ready.page.isOffline
            metrics = ready.page.metrics
            cards = ready.page.cards
            toastMessage = ready.activeToast ?? ready.page.lastFeedbackMessage

        case let failed as WatchUiStateError:
            isLoading = false
            errorMessage = failed.error.message

        default:
            break
        }
    }

    private func startObservingState() {
        observeTask?.cancel()
        observeTask = Task { [weak self] in
            while !Task.isCancelled {
                self?.syncFromControllerState()
                try? await Task.sleep(nanoseconds: 150_000_000)
            }
        }
    }
}

struct AbetaWatchSduiView: View {
    @StateObject private var viewModel: AbetaWatchViewModel

    init(bffBaseUrl: String? = nil) {
        _viewModel = StateObject(wrappedValue: AbetaWatchViewModel(bffBaseUrl: bffBaseUrl))
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading && viewModel.metrics.isEmpty && viewModel.cards.isEmpty {
                    ProgressView("Loading…")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let errorMessage = viewModel.errorMessage {
                    errorStateView(message: errorMessage)
                } else {
                    readyContentView
                }
            }
            .navigationTitle(viewModel.titleText)
        }
    }

    private var readyContentView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                if viewModel.isOffline {
                    Label("Offline · Cached", systemImage: "wifi.slash")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                        .accessibilityLabel("Offline mode showing cached screen")
                }

                if let toast = viewModel.toastMessage {
                    Button {
                        viewModel.dismissToast()
                    } label: {
                        Text(toast)
                            .font(.caption2)
                            .foregroundStyle(.primary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.bordered)
                    .tint(.green)
                    .accessibilityLabel(toast)
                    .accessibilityHint("Tap to dismiss notification")
                }

                ForEach(viewModel.metrics, id: \.id) { metric in
                    metricRow(metric)
                }

                ForEach(viewModel.cards, id: \.id) { card in
                    actionCardRow(card)
                }
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 4)
        }
    }

    @ViewBuilder
    private func metricRow(_ metric: WatchMetricGlance) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(metric.label)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(metric.value)
                .font(.headline)
                .foregroundStyle(.primary)
            if let fraction = metric.progressFraction {
                ProgressView(value: Double(max(0, min(1, fraction.floatValue))))
                    .tint(.accentColor)
            }
            if let progressLabel = metric.progressLabel {
                Text(progressLabel)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .accessibilityElement(children: .combine)
        .onTapGesture {
            viewModel.trigger(metric.onClick)
        }
    }

    @ViewBuilder
    private func actionCardRow(_ card: WatchActionCard) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                Text(card.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                if let badge = card.badgeLabel {
                    Text(badge)
                        .font(.caption2)
                        .foregroundStyle(.green)
                }
            }
            if let subtitle = card.subtitle {
                Text(subtitle)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            if let primaryLabel = card.primaryActionLabel {
                Button {
                    viewModel.trigger(card.primaryAction)
                } label: {
                    Text(primaryLabel)
                        .font(.caption2)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(card.primaryAction == nil)
                .accessibilityLabel(primaryLabel)
            }
            if let secondaryLabel = card.secondaryActionLabel {
                Button {
                    viewModel.trigger(card.secondaryAction)
                } label: {
                    Text(secondaryLabel)
                        .font(.caption2)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(card.secondaryAction == nil)
                .accessibilityLabel(secondaryLabel)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func errorStateView(message: String) -> some View {
        VStack(spacing: 8) {
            Text(message)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button("Retry") {
                viewModel.refresh()
            }
            .buttonStyle(.borderedProminent)
            .accessibilityLabel("Retry loading screen")
        }
        .padding()
    }
}
