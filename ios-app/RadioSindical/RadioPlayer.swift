import AVFoundation
import Combine
import MediaPlayer
import Foundation

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
            songs = try JSONDecoder().decode(RadioCatalogResponse.self, from: data).tracks
                .filter { $0.id > 0 && !$0.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
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

    private func songDidFinish() {
        guard !songs.isEmpty else { return }
        completedSongs += 1
        let index = songs.firstIndex(where: { $0.id == selected?.id }) ?? 0
        let following = songs[(index + 1) % songs.count]
        if completedSongs % 2 == 0 {
            select(following, autoplay: false)
            let artist = following.artist.trimmingCharacters(in: .whitespacesAndNewlines)
            let text = "Soy DeVi. Sigue \(following.title)\(artist.isEmpty ? "" : ", de \(artist)"). ¡Que la disfrutes!"
            let announcement = AVSpeechUtterance(string: String(text.prefix(300)))
            announcement.voice = AVSpeechSynthesisVoice(language: "es-MX")
            announcement.rate = 0.52
            resumeAfterVoice = true
            voice.speak(announcement)
        } else {
            select(following, autoplay: true)
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            if resumeAfterVoice { resumeAfterVoice = false; resume() }
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in resumeAfterVoice = false }
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
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
