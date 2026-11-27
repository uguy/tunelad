package org.tunelad.media.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({FfmpegProperties.class, FfprobeProperties.class})
class MediaConfig {
}
