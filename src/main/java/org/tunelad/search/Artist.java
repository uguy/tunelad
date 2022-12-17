package org.tunelad.search;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

public record Artist(
		@Field(type = FieldType.Text)
		String name
) {}
