// Single-file reference SwiftUI hosts for teams vendoring Apple source directly
// instead of importing the `AbetaSduiSwiftUI` and `AbetaWatchSwiftUI` SwiftPM libraries.

// MARK: - 1. iOS & iPadOS SDUI Host (`AbetaSduiKit.xcframework`)

#if canImport(SwiftUI) && os(iOS)
import SwiftUI
import UIKit
import AbetaSduiKit

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
        navigationContainer {
            AbetaIosSduiScreenView(
                screenId: activeScreenId,
                bffBaseUrl: bffBaseUrl
            )
            .id(activeScreenId)
            .ignoresSafeArea(.container, edges: .all)
        }
        .onOpenURL { url in
            if let resolved = resolveScreenId(from: url), !resolved.isEmpty {
                activeScreenId = resolved
            }
        }
    }

    @ViewBuilder
    private func navigationContainer<Content: View>(
        @ViewBuilder content: () -> Content
    ) -> some View {
        if #available(iOS 16.0, *) {
            NavigationStack {
                content()
                    .toolbar(.hidden, for: .navigationBar)
            }
        } else {
            NavigationView {
                content()
                    .navigationBarHidden(true)
            }
            .navigationViewStyle(.stack)
        }
    }

    private func resolveScreenId(from url: URL) -> String? {
        if let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
           let queryScreen = components.queryItems?.first(where: { $0.name == "screen" })?.value?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !queryScreen.isEmpty {
            return queryScreen
        }
        let pathSegments = url.pathComponents.filter { $0 != "/" && !$0.isEmpty }
        if let screensIdx = pathSegments.firstIndex(of: "screens"),
           pathSegments.indices.contains(screensIdx + 1) {
            return pathSegments[screensIdx + 1]
        }
        let scheme = url.scheme?.lowercased()
        let isHttpScheme = scheme == "http" || scheme == "https"
        if !isHttpScheme, url.host == "screens", let firstSegment = pathSegments.first {
            return firstSegment
        }
        if let lastSegment = pathSegments.last {
            return lastSegment
        }
        if !isHttpScheme, let host = url.host?.trimmingCharacters(in: .whitespacesAndNewlines), !host.isEmpty {
            return host
        }
        return nil
    }
}
#endif

// MARK: - 2. Apple Watch (`AbetaWatchKit.xcframework`)

#if canImport(SwiftUI) && os(watchOS)
import Foundation
import SwiftUI
import AbetaWatchKit

private struct HostWatchUiStateBox: @unchecked Sendable {
    let value: WatchUiState
}

private final class HostWatchCancelHandle: @unchecked Sendable {
    private let lock = NSLock()
    private var onCancel: (() -> Void)?

    init(_ onCancel: @escaping () -> Void) {
        self.onCancel = onCancel
    }

    func cancel() {
        lock.lock()
        let block = onCancel
        onCancel = nil
        lock.unlock()
        block?()
    }
}

private final class HostWatchLifecycle: @unchecked Sendable {
    let holder: WatchCompanionHostHolder
    private let lock = NSLock()
    private var stateCancel: (() -> Void)?
    private var connectivityCancel: (() -> Void)?
    private var isClosed: Bool = false

    init(holder: WatchCompanionHostHolder) {
        self.holder = holder
    }

    func replaceHandles(
        state: @escaping () -> Void,
        connectivity: @escaping () -> Void
    ) {
        lock.lock()
        let prevState = stateCancel
        let prevConnectivity = connectivityCancel
        if isClosed {
            stateCancel = nil
            connectivityCancel = nil
            lock.unlock()
            prevState?()
            prevConnectivity?()
            state()
            connectivity()
            return
        }
        stateCancel = state
        connectivityCancel = connectivity
        lock.unlock()
        prevState?()
        prevConnectivity?()
    }

    func stop() {
        lock.lock()
        let prevState = stateCancel
        let prevConnectivity = connectivityCancel
        stateCancel = nil
        connectivityCancel = nil
        lock.unlock()
        prevState?()
        prevConnectivity?()
    }

    func close() {
        lock.lock()
        let prevState = stateCancel
        let prevConnectivity = connectivityCancel
        stateCancel = nil
        connectivityCancel = nil
        let shouldClose = !isClosed
        isClosed = true
        lock.unlock()
        prevState?()
        prevConnectivity?()
        if shouldClose {
            holder.close()
        }
    }
}

@MainActor
final class AbetaWatchViewModel: ObservableObject {
    private let lifecycle: HostWatchLifecycle

    private var holder: WatchCompanionHostHolder { lifecycle.holder }

    @Published var metrics: [WatchMetricGlance] = []
    @Published var cards: [WatchActionCard] = []
    @Published var titleText: String = "Abeta Watch"
    @Published var isOffline: Bool = false
    @Published var isLoading: Bool = true
    @Published var errorMessage: String? = nil
    @Published var toastMessage: String? = nil

    init(bffBaseUrl: String? = nil, initialScreenId: String = "watch-status") {
        let createdHolder = WatchAppleFactoryKt.createAppleWatchCompanionHost(bffBaseUrl: bffBaseUrl)
        self.lifecycle = HostWatchLifecycle(holder: createdHolder)
        startObservingState()
        createdHolder.controller.loadScreen(screenId: initialScreenId)
    }

    deinit {
        lifecycle.close()
    }

    func stop() {
        lifecycle.stop()
    }

    func refresh() {
        isLoading = true
        errorMessage = nil
        holder.controller.refresh()
        syncFromControllerState()
    }

    /// SwiftUI `.refreshable` compatible `async`/`await` reload awaiting the next settled state.
    func refreshAsync() async {
        isLoading = true
        errorMessage = nil
        let settled = await holder.refreshAndAwaitReady()
        apply(state: settled)
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
        apply(state: holder.controller.uiState.value)
    }

    private func apply(state rawState: Any?) {
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
        let stateCancel = holder.observeUiState { [weak self] state in
            let boxed = HostWatchUiStateBox(value: state)
            Task { @MainActor [weak self] in
                self?.apply(state: boxed.value)
            }
        }
        let connectivityCancel = holder.startConnectivityObservation()
        lifecycle.replaceHandles(state: stateCancel, connectivity: connectivityCancel)
    }
}

struct AbetaWatchSduiView: View {
    @StateObject private var viewModel: AbetaWatchViewModel

    @MainActor
    init(bffBaseUrl: String? = nil, initialScreenId: String = "watch-status") {
        _viewModel = StateObject(
            wrappedValue: AbetaWatchViewModel(bffBaseUrl: bffBaseUrl, initialScreenId: initialScreenId)
        )
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
            .navigationBarTitleDisplayMode(.inline)
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

                if let toast = viewModel.toastMessage, !toast.isEmpty {
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
        .refreshable {
            await viewModel.refreshAsync()
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
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .onTapGesture {
            viewModel.trigger(metric.onClick)
        }
    }

    @ViewBuilder
    private func actionCardRow(_ card: WatchActionCard) -> some View {
        if card.primaryActionLabel == nil,
           card.secondaryActionLabel == nil,
           let cardAction = card.primaryAction {
            Button {
                viewModel.trigger(cardAction)
            } label: {
                actionCardContent(card)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isButton)
        } else {
            actionCardContent(card)
                .contentShape(Rectangle())
                .onTapGesture {
                    if card.primaryActionLabel == nil, let cardAction = card.primaryAction {
                        viewModel.trigger(cardAction)
                    }
                }
        }
    }

    private func actionCardContent(_ card: WatchActionCard) -> some View {
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

// MARK: - 3. Swift Concurrency (`async`/`await` + `AsyncStream`) Ergonomics

extension WatchCompanionHostHolder {
    /// Emits `WatchUiState` updates as a reactive, cancellation-aware Swift `AsyncStream`
    /// backed by `observeUiState` with zero polling overhead.
    func uiStateStream() -> AsyncStream<WatchUiState> {
        AsyncStream { continuation in
            let cancelHandle = HostWatchCancelHandle(
                self.observeUiState { state in
                    continuation.yield(state)
                }
            )
            continuation.onTermination = { _ in
                cancelHandle.cancel()
            }
        }
    }

    /// Triggers `loadScreen(screenId:)` and suspends until a settled `Ready` or `Error` state arrives.
    func loadScreenAndAwaitReady(
        _ screenId: String = "watch-status"
    ) async -> WatchUiState {
        controller.loadScreen(screenId: screenId)
        for await state in uiStateStream() {
            if state is WatchUiStateReady || state is WatchUiStateError {
                return state
            }
        }
        return (controller.uiState.value as? WatchUiState) ?? WatchUiStateIdle.shared
    }

    /// Triggers `refresh()` and suspends until the refreshed screen settles (`Ready` or `Error`).
    func refreshAndAwaitReady() async -> WatchUiState {
        controller.refresh()
        for await state in uiStateStream() {
            if state is WatchUiStateReady || state is WatchUiStateError {
                return state
            }
        }
        return (controller.uiState.value as? WatchUiState) ?? WatchUiStateIdle.shared
    }
}
#endif
