import AVFoundation
import Combine
import MediaPlayer
import Foundation
import UIKit

@MainActor final class RadioPlayer: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    static let shared = RadioPlayer()

    @Published private(set) var songs: [RadioSong] = []
    @Published private(set) var selected: RadioSong?
    @Published private(set) var lyrics = ""
    @Published private(set) var currentSeconds: Double = 0
    @Published private(set) var durationSeconds: Double = 0
    @Published private(set) var isPlaying = false
    @Published private(set) var message: String?

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private let voice = AVSpeechSynthesizer()
    private var completedSongs = 0
    private var resumeAfterVoice = false
    private var lyricsRequest = UUID()
    private var commercials: [RadioSong] = []
    private var commercialIntervalMinutes = 0
    private var facts: [RadioFact] = []
    private var commercialIndex = 0
    private var elapsedMusicSeconds: Double = 0
    private var previousFirstId = UserDefaults.standard.integer(forKey: "radioLastFirstId")
    private var pendingIntroduction = false
    private var pendingFact = false
    private var lastFactId: String?
    private var queuedAfterCommercial: RadioSong?
    private var isCommercial = false
    private var artwork: [Int: MPMediaItemArtwork] = [:]
    private var artworkUpdatedAt: [Int: Date] = [:]

    private override init() {
        super.init()
        voice.delegate = self
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            message = "No se pudo activar el audio en segundo plano."
        }
        configureRemoteControls()
    }

    func refresh() async {
        guard let url = URL(string: "https://sntss1puebla.com/api/radio/catalog") else { return }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            let program = try JSONDecoder().decode(RadioCatalogResponse.self, from: data)
            let incoming = program.tracks.filter { $0.id > 0 && !$0.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            let previous = songs
            if previous.isEmpty {
                var mixed = incoming.shuffled()
                if mixed.count > 1 && mixed[0].id == previousFirstId { mixed.swapAt(0, 1) }
                songs = mixed
                if let first = mixed.first {
                    previousFirstId = first.id
                    UserDefaults.standard.set(first.id, forKey: "radioLastFirstId")
                }
            } else {
                songs = previous.compactMap { old in incoming.first { $0.id == old.id } }
                    + incoming.filter { fresh in !previous.contains(where: { $0.id == fresh.id }) }.shuffled()
            }
            commercials = program.commercials.filter { $0.id > 0 }
            commercialIntervalMinutes = (0...180).contains(program.commercialIntervalMinutes) ? program.commercialIntervalMinutes : 0
            facts = program.facts
            if let active = selected, let latest = incoming.first(where: { $0.id == active.id }) {
                selected = latest
                if latest.artworkURL == nil {
                    artwork.removeValue(forKey: latest.id)
                    artworkUpdatedAt.removeValue(forKey: latest.id)
                }
                updateNowPlaying()
                if let url = latest.artworkURL,
                   artworkUpdatedAt[latest.id].map({ Date().timeIntervalSince($0) > 300 }) ?? true {
                    Task { await fetchArtwork(latest.id, url: url) }
                }
            }
            message = songs.isEmpty ? "Todavía no hay canciones disponibles." : nil
        } catch {
            message = "No se pudo cargar la radio. Comprueba tu conexión."
        }
    }

    func play(_ song: RadioSong) { select(song, autoplay: true) }

    func togglePlayback() {
        if isPlaying || voice.isSpeaking { pause() }
        else if player != nil { resume() }
        else if let first = songs.first { select(first, autoplay: true) }
    }

    func pause() {
        resumeAfterVoice = false
        if voice.isSpeaking { voice.stopSpeaking(at: .immediate) }
        player?.pause()
        isPlaying = false
        updateNowPlaying()
    }

    func resume() {
        guard player != nil else { return }
        player?.play()
        isPlaying = true
        updateNowPlaying()
    }

    func next() {
        isCommercial = false
        queuedAfterCommercial = nil
        guard !songs.isEmpty else { return }
        let index = songs.firstIndex(where: { $0.id == selected?.id }) ?? -1
        select(songs[(index + 1) % songs.count], autoplay: true)
    }

    func previous() {
        guard !songs.isEmpty else { return }
        let index = songs.firstIndex(where: { $0.id == selected?.id }) ?? 0
        select(songs[(index - 1 + songs.count) % songs.count], autoplay: true)
    }

    func seek(to seconds: Double) {
        guard seconds.isFinite && seconds >= 0 else { return }
        player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
        currentSeconds = seconds
        updateNowPlaying()
    }

    private func select(_ song: RadioSong, autoplay: Bool) {
        isCommercial = false
        resumeAfterVoice = false
        if voice.isSpeaking { voice.stopSpeaking(at: .immediate) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
        if let timeObserver, let player { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        player?.pause()

        selected = song
        lyrics = ""
        currentSeconds = 0
        durationSeconds = 0
        let item = AVPlayerItem(url: song.audioURL)
        let nextPlayer = AVPlayer(playerItem: item)
        player = nextPlayer
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.songDidFinish() }
        }
        timeObserver = nextPlayer.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main
        ) { [weak self] time in
            Task { @MainActor in
                guard let self, self.selected?.id == song.id else { return }
                self.currentSeconds = max(0, time.seconds.isFinite ? time.seconds : 0)
                let duration = nextPlayer.currentItem?.duration.seconds ?? 0
                self.durationSeconds = duration.isFinite && duration > 0 ? duration : 0
                self.updateNowPlaying()
            }
        }
        isPlaying = autoplay
        if autoplay { nextPlayer.play() }
        updateNowPlaying()
        let request = UUID()
        lyricsRequest = request
        Task { await fetchLyrics(song.id, request: request) }
        if let url = song.artworkURL, artwork[song.id] == nil {
            Task { await fetchArtwork(song.id, url: url) }
        }
    }

    private func playCommercial(_ commercial: RadioSong, then next: RadioSong) {
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
        if let timeObserver, let player { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        player?.pause()
        isCommercial = true
        queuedAfterCommercial = next
        let item = AVPlayerItem(url: commercial.commercialURL)
        let commercialPlayer = AVPlayer(playerItem: item)
        player = commercialPlayer
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime,
            object: item, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let self, let next = self.queuedAfterCommercial else { return }
                self.queuedAfterCommercial = nil
                self.select(next, autoplay: true)
                self.introduceIfDue()
            }
        }
        commercialPlayer.play()
        isPlaying = true
        // If this upload has become unavailable, keep the station playing.
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            if isCommercial && commercialPlayer.currentItem?.status == .failed,
               let next = queuedAfterCommercial {
                queuedAfterCommercial = nil
                select(next, autoplay: true)
                introduceIfDue()
            }
        }
    }

    private func fetchLyrics(_ id: Int, request: UUID) async {
        guard let url = URL(string: "https://sntss1puebla.com/api/radio/lyrics/\(id)") else { return }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return }
            let value = try JSONDecoder().decode(RadioLyricResponse.self, from: data).lyrics
            if selected?.id == id && lyricsRequest == request { lyrics = String(value.prefix(12_000)) }
        } catch { /* The song remains playable without lyrics. */ }
    }

    private func fetchArtwork(_ id: Int, url: URL) async {
        guard let (data, response) = try? await URLSession.shared.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200,
              data.count <= 3_000_000, let image = UIImage(data: data) else { return }
        artwork[id] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        artworkUpdatedAt[id] = Date()
        if artwork.count > 8, let old = artwork.keys.first(where: { $0 != id }) { artwork.removeValue(forKey: old) }
        if selected?.id == id { updateNowPlaying() }
    }

    private func songDidFinish() {
        guard !songs.isEmpty else { return }
        elapsedMusicSeconds += max(currentSeconds, durationSeconds)
        completedSongs += 1
        pendingIntroduction = completedSongs % 2 == 0
        pendingFact = completedSongs % 5 == 0
        let index = songs.firstIndex(where: { $0.id == selected?.id }) ?? 0
        if songs.count > 1 && index == songs.count - 1 {
            var mixed = songs.shuffled()
            if mixed.first?.id == selected?.id { mixed.swapAt(0, 1) }
            songs = mixed
        }
        let following = songs.count > 1 && index == songs.count - 1
            ? songs[0] : songs[(index + 1) % songs.count]
        if commercialIntervalMinutes >= 5 && !commercials.isEmpty &&
           elapsedMusicSeconds >= Double(commercialIntervalMinutes * 60) {
            let commercial = commercials[commercialIndex % commercials.count]
            commercialIndex += 1
            elapsedMusicSeconds = 0
            playCommercial(commercial, then: following)
            return
        }
        select(following, autoplay: true)
        introduceIfDue()
    }

    private func introduceIfDue() {
        guard pendingIntroduction || pendingFact else { return }
        let fact = pendingFact ? (facts.filter { $0.id != lastFactId }.randomElement() ?? facts.randomElement()) : nil
        if let fact { lastFactId = fact.id }
        let introduction = pendingIntroduction && selected != nil
            ? "¡Seguimos con \(selected!.title), de \(selected!.artist.isEmpty ? "Radio Sindical" : selected!.artist)! Soy DeVi; que la disfrutes."
            : ""
        pendingIntroduction = false
        pendingFact = false
        let words = [fact.map { "¿Sabías que? \($0.text)" } ?? "", introduction]
            .filter { !$0.isEmpty }.joined(separator: " ")
        guard !words.isEmpty else { return }
        // Music stays audible at a reduced volume, like the portal's radio presenter.
        player?.volume = 0.35
        let announcement = AVSpeechUtterance(string: String(words.prefix(800)))
        announcement.voice = AVSpeechSynthesisVoice(language: "es-MX")
        announcement.rate = 0.52
        voice.speak(announcement)
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            player?.volume = 1
            if resumeAfterVoice { resumeAfterVoice = false; resume() }
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in resumeAfterVoice = false; player?.volume = 1 }
    }

    private func configureRemoteControls() {
        let controls = MPRemoteCommandCenter.shared()
        controls.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }; return .success
        }
        controls.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }; return .success
        }
        controls.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.next() }; return .success
        }
        controls.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.previous() }; return .success
        }
        controls.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(to: event.positionTime) }; return .success
        }
    }

    private func updateNowPlaying() {
        guard let selected else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: selected.title,
            MPMediaItemPropertyArtist: selected.artist.isEmpty ? "Radio Sindical" : selected.artist,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentSeconds,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1 : 0,
        ]
        if let seconds = player?.currentItem?.duration.seconds, seconds.isFinite && seconds > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = seconds
        }
        if let cover = artwork[selected.id] { info[MPMediaItemPropertyArtwork] = cover }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
