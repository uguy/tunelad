# Tune Lad

A collection of traditional Irish music: recordings annotated with the tunes they contain, so that people can build and learn new sets from them.

## Language

### Music

**Tune**:
A traditional Irish piece, identified by details that never change between recordings: name, aliases, author and Dance type. It may carry a usual key as a hint, and one Reference transcription. Imported from thesession.org by id, which it then keeps, or in bulk from its data dump by an Administrator (first setting only, with the composer as author); an Account can create one by hand when no source matches.
_Avoid_: Piece, melody, Setting

**Dance type**:
The kind of Tune: jig, reel, hornpipe, slide, polka and so on.
_Avoid_: Rhythm, genre

**Set**:
An ordered sequence of Tunes played one after another, such as a set of reels, with an optional name. It comes either from a Track (it mirrors that Track's Performances and follows the Track) or from an Account, its owner, who creates and edits it, with Tunes they play or not. It exists on its own, with or without recordings. Every Set is visible to all Accounts; an Account can share a Set by adding it to its own sets, never by copying it. Two Sets may hold the same Tunes.
_Avoid_: Suite, medley, plan, Prepared set

**Track**:
An audio recording, by one Artist, of a Set (a single Tune counts as a Set of one) once it has one: a Track uploaded from its audio file alone has no Set yet. The audio file is its only required part; it always has a title (the file name until the file's tags give a better one). It carries a title, a description, tags and a source (where the recording comes from, as free text).
_Avoid_: Song, media

**Artist**:
A musician or a band that performs Tracks: a Musician, a Band, or not yet classified (as when imported by name only). A shared record, created on first use and matched by name, ignoring case and surrounding whitespace.
_Avoid_: Performer, brand

**Band**:
An Artist made of several Musicians, its members.
_Avoid_: Group

**Musician**:
An Artist who is one person, and who may be a member of Bands.
_Avoid_: Player

**Performance**:
One Tune as played in one Track (or, when imported from a recording whose tune is not identified, only the listed tune name, unresolved): its order and start offset in the Track, its own key, notes on melodic variation, and its Transcription. This is where an interpretation is kept, since each recording of a Tune may differ.
_Avoid_: Version, rendition

**Transcription**:
The ABC notation of a Performance, from which the music sheet is drawn. Shown on the Tune detail view for each Performance.
_Avoid_: Sheet, score

**Reference transcription**:
The ABC notation a Tune carries on its own, imported from a source such as thesession.org. It is not from any recording, and a Performance's Transcription takes precedence over it.

### Identification

**Identification**:
The automatic analysis of a Track's audio, run in the background after upload. It reads the file's tags (title, artist, album, comment) to fill the Track's fields that no Account provided, and proposes where each Tune of its Set starts, by recognising the Tunes in the audio (Transcriptions are still written by hand). A Track's Performances are pending (not analysed yet, or the analysis could not be done), identified (proposed by the machine), nothing found (an analysis finished without hearing the Tune) or corrected (edited by an Account); manual corrections are never overwritten by a re-run. For a Track that has no Set, Identification listens for any Tune and derives the Set itself: the Tunes it is confident about (importing from thesession.org those the catalogue lacks), in the order first heard, become the Track's Set, found or created like any Set, with one identified Performance each. A Set is given once, and never replaces one an Account chose.
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

**Repertoire**:
The Tunes an Account says it plays. Visible to every Account, so that others can see who plays a Tune and build Sets around what someone already plays.
_Avoid_: Tunebook

**Comment**:
A remark an Account leaves on a Track, a Tune or a Set.
_Avoid_: Review, note

**Account's sets**:
The Sets an Account created plus the Sets it added by reference. This is what others read to see what someone plays.

### Catalogue

**Album**:
A release by one Artist grouping Tracks, with a title and optionally a cover and a release date. Imported from thesession.org recordings or created by hand.

**Catalogue Track**:
A Track that has no audio yet, only a place on an Album. Audio uploaded later can be attached to it.

### Behaviour

**Live update**:
A notice to a connected browser that something it displays (a Track, Favourite or Playlist) has changed.

## Relationships

- A **Track** is a recording of one **Set** and contains one **Performance** per Tune of that Set, in order.
- A **Performance** is of exactly one **Tune**; a **Tune** has many Performances, one per recording.
- A **Set** is a sequence of **Tunes**. A Track-derived Set has exactly one **Track**; an Account-created Set has an owner and no Track.
- A **Band** has many **Musicians**; an **Artist** performs many **Tracks**.
- A **Track** stored before Sets existed has no **Set** and no **Performances** yet.
- Deleting a **Track** removes it from every **Playlist** and **Favourite**.
