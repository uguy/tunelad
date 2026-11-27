## ADDED Requirements

### Requirement: Comment events reach every connection
The system SHALL announce a change to the Comments of a Track, a Tune or a Set (posted, edited, deleted, including
deletion with the Track) with the event `comment.changed`, whose `id` is the id of the subject. It SHALL reach every
connection, anonymous or authenticated, on every running instance, because Comments are public. The event SHALL NOT
carry the Comment's text.

#### Scenario: Comment posted
- **GIVEN** an anonymous client and a client logged in as bob are connected
- **WHEN** alice posts a Comment on Tune 12
- **THEN** both receive `comment.changed` with id `12`

#### Scenario: Track deleted
- **GIVEN** Track 42 has Comments and a client is connected
- **WHEN** Track 42 is deleted
- **THEN** the client receives `comment.changed` with id `42`

### Requirement: The UI follows comments live
The UI SHALL refetch the Comments it shows when it receives `comment.changed` for the displayed subject, and after its
live connection reopens.

#### Scenario: Comment posted elsewhere
- **GIVEN** a Track page is open in one browser
- **WHEN** another Account comments on that Track
- **THEN** the first page lists the new Comment without a reload
