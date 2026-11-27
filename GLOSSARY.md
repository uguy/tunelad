# Tune Lad

A collection of traditional Irish music: recordings annotated with the tunes they contain, so that people can build and learn new sets from them.

## Language

### Music

**Tune**:
A traditional Irish piece, identified by details that never change between recordings: name, aliases, author and Dance type. It may carry a usual key as a hint, and one Reference transcription. Imported from thesession.org by id, which it then keeps; an Account can create one by hand when no source matches.
_Avoid_: Piece, melody, Setting

**Dance type**:
The kind of Tune: jig, reel, hornpipe, slide, polka and so on.
_Avoid_: Rhythm, genre

**Set**:
An ordered sequence of Tunes played one after another, such as a set of reels. It exists on its own, with or without recordings: a Set no Track points to yet is a plan. One Set exists per distinct ordered list of Tunes, and is shared by all Accounts.
_Avoid_: Suite, medley, plan

**Track**:
An audio recording, by one Artist, of a Set (a single Tune counts as a Set of one). It carries a title, a description, tags and a source (where the recording comes from, as free text).
_Avoid_: Song, media

**Artist**:
The musician or band that performs a Track. A shared record, created on first use and matched by name, ignoring case and surrounding whitespace.
_Avoid_: Performer, brand

**Performance**:
One Tune as played in one Track: its order and start offset in the Track, its own key, notes on melodic variation, and its Transcription. This is where an interpretation is kept, since each recording of a Tune may differ.
_Avoid_: Version, rendition

**Transcription**:
The ABC notation of a Performance, from which the music sheet is drawn. Shown on the Tune detail view for each Performance.
_Avoid_: Sheet, score

**Reference transcription**:
The ABC notation a Tune carries on its own, imported from a source such as thesession.org. It is not from any recording, and a Performance's Transcription takes precedence over it.

### Identification

**Identification**:
The automatic analysis of a Track's audio, run in the background after upload, that proposes where each Tune of its Set starts, by recognising the Tunes in the audio (Transcriptions are still written by hand). A Track's Performances are pending (not analysed yet, or the analysis could not be done), identified (proposed by the machine), nothing found (an analysis finished without hearing the Tune) or corrected (edited by an Account); manual corrections are never overwritten by a re-run.
_Avoid_: Recognition, tagging

### People and lists

**Account**:
A person using Tune Lad, created the first time their identity authenticates.
_Avoid_: User

**Owner**:
The Account that uploaded a Track. Only the Owner or an Administrator may change the Track and its Performances.

**Administrator**:
An Account that currently holds the identity provider's admin role. A role of the session, not a stored property of the Account.

**Playlist**:
A named, ordered, private list of Tracks that an Account groups for listening. It holds Tracks, not Tunes, because Tracks carry the audio.

**Favourite**:
A Track an Account has marked, listed most recent first. Private to the Account.

**Learning list**:
The Sets an Account has planned or is learning. Private to the Account, and separate from Playlist.

### Behaviour

**Live update**:
A notice to a connected browser that something it displays (a Track, Favourite or Playlist) has changed.

## Relationships

- A **Track** is a recording of one **Set** and contains one **Performance** per Tune of that Set, in order.
- A **Performance** is of exactly one **Tune**; a **Tune** has many Performances, one per recording.
- A **Set** is a sequence of **Tunes** and has zero or more **Tracks**.
- A **Track** stored before Sets existed has no **Set** and no **Performances** yet.
- Deleting a **Track** removes it from every **Playlist** and **Favourite**.
