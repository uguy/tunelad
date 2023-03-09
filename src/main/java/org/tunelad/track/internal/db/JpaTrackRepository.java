package org.tunelad.track.internal.db;

import org.tunelad.track.internal.Track;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTrackRepository extends JpaRepository<Track, String> {
}
