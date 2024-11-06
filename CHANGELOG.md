# Changelog

## [1.3.1](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v1.3.0...v1.3.1) (2024-11-06)


### Bug Fixes

* An optimization in guava 33.3.0 broke our tests by not calling `addListener()` on futures which are already `isDone()`. Add mock call to make our futures not `isDone()` in some tests. ([0907353](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/0907353b27144e7bd2a57439efbb9ce2da304ab3))
* MaxOutstandingMessages should be defined without maxOutstandingRequestBytes ([#349](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/349)) ([4c107d8](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/4c107d896930492a0794d0c133104d5574f92efc))


### Dependencies

* Depend on pubsublite libraries from google-cloud-bom ([0907353](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/0907353b27144e7bd2a57439efbb9ce2da304ab3))
* Update flogger to 0.8 ([0907353](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/0907353b27144e7bd2a57439efbb9ce2da304ab3))
* Update google-cloud-shared-config to 1.11.3 ([#357](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/357)) ([0907353](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/0907353b27144e7bd2a57439efbb9ce2da304ab3))
* Update google-cloud-shared-dependencies to 3.39.0 ([0907353](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/0907353b27144e7bd2a57439efbb9ce2da304ab3))
* Update pubsublite-kafka to 1.2.2 ([0907353](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/0907353b27144e7bd2a57439efbb9ce2da304ab3))
* Use google-cloud-bom 0.231.0 instead of libraries-bom ([0907353](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/0907353b27144e7bd2a57439efbb9ce2da304ab3))

## [1.3.0](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v1.2.0...v1.3.0) (2024-10-07)


### Features

* Add kafka migration ([#330](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/330)) ([58e7555](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/58e7555831e63264d0b7fda19291629105a085ff))


### Bug Fixes

* Report connector package version instead of kafka connect version ([#266](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/266)) ([33f2761](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/33f2761b303f267b4fa6af9e2acf88a71b210f4b))


### Dependencies

* Update dependency org.apache.commons:commons-lang3 to v3.14.0 ([#302](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/302)) ([d5cad34](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/d5cad346dfd68c5c86186bbc44a5acf2174760cb))
* Update dependency org.slf4j:slf4j-api to v2.0.13 ([#299](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/299)) ([bc00e06](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/bc00e06b260de129dc6c0e5e2d36c6fa0e393ffa))
* Update kafka-clients and pubsublite-kafka and pubsublite clients ([#353](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/353)) ([773f67b](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/773f67b2bd8c20c9620b05c0c09a334ceac22567))

## [1.2.0](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v1.1.0...v1.2.0) (2023-05-10)


### Features

* Add the ability to set gRPC compression in the CPS sink connector. ([#259](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/259)) ([ad1e72b](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/ad1e72b7a09d6034c5ec0009131f39aaef236a50))

## [1.1.0](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v1.0.0...v1.1.0) (2023-03-31)


### Features

* Add pubsublite sink support for credentials settings ([#251](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/251)) ([7290786](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/7290786e82db354f1d310c599f84150a7ec0ef8b))
* Add pubsublite.ordering.mode to kafka connector ([#228](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/228)) ([c499c39](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/c499c395cef38f9bb4b52d157bc336bff0644b94))


### Bug Fixes

* **main:** Typo in README.md for PubSubLiteSourceConnector ([#242](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/242)) ([d881eaf](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/d881eafef9bf5ece2391a75bc3d2cb6208a20ba9))


### Dependencies

* Update dependency com.google.cloud:google-cloud-shared-dependencies to v3.4.0 ([#232](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/232)) ([f10f9a6](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/f10f9a6546eb6ea65b61fbbe4538edae81b524ab))
* Update dependency com.google.cloud:google-cloud-shared-dependencies to v3.5.0 ([#244](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/244)) ([435cd98](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/435cd98e3ae81f3d698e174782ac81199bdf756f))
* Update dependency com.google.cloud:google-cloud-shared-dependencies to v3.6.0 ([#250](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/250)) ([888d3d9](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/888d3d95ea01ae1535e6aba47fa71c6cb0f0e7e1))
* Update dependency org.slf4j:slf4j-api to v2.0.6 ([#230](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/230)) ([9dec71b](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/9dec71b8df9acdd738b4738ee060b13ff602e86b))
* Update dependency org.slf4j:slf4j-api to v2.0.7 ([#243](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/243)) ([1e1c336](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/1e1c33666ee1e0ef3fd45684054dcf25216004f8))

## [1.0.0](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v0.1.5...v1.0.0) (2022-11-18)


### Features

* Mark stable to prepare for 1.0.0 release ([#198](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/198)) ([4f8a702](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/4f8a702863aa11d9a685286b601ad07df48ed785))


### Dependencies

* Update cloud-compute.version to v1.16.0 ([#191](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/191)) ([980ba98](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/980ba98724f771ae190ac3a984c8207a2c9214d8))
* Update dependency com.google.auth:google-auth-library-credentials to v1.13.0 ([#189](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/189)) ([4367fcd](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/4367fcd88d38cfdca9893518ea795a00437f7fc2))

## [0.1.5](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v0.1.4...v0.1.5) (2022-11-14)


### Dependencies

* Update dependency com.google.api.grpc:proto-google-cloud-pubsub-v1 to v1.102.25 ([#184](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/184)) ([36814e1](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/36814e12b4f96a0edfff117fc065bdc75c59b1e2))

## [0.1.4](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v0.1.3...v0.1.4) (2022-11-10)


### Dependencies

* Update dependency com.google.cloud:google-cloud-core to v2.8.28 ([#179](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/179)) ([e48d1e1](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/e48d1e118f03ef364db77c6c52a762ba8f455de9))

## [0.1.3](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v0.1.2...v0.1.3) (2022-11-09)


### Bug Fixes

* Cleanup CPS source subscription in tearDown() ([#165](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/165)) ([73d5dcb](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/73d5dcbf018e20674e1b56c70e79dd3dfd5b4dbd))


### Documentation

* Add auth info and add annotation ([#144](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/144)) ([d1005ed](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/d1005ed70fb595bbfcc268fdd68d4db782b22036))


### Dependencies

* Update cloud-compute.version to v1.15.0 ([#154](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/154)) ([ec038c7](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/ec038c7a6042fed84e841ffe75e16f15528adc56))
* Update dependency com.google.api:api-common to v2.2.2 ([#171](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/171)) ([220b6c7](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/220b6c7503447cc6a485a96816d376d1c5c9a5cd))
* Update dependency com.google.auth:google-auth-library-credentials to v1.12.1 ([#157](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/157)) ([dc67ea6](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/dc67ea6b658a635f919af3c4305ef56dfc6852c1))
* Update dependency com.google.auth:google-auth-library-oauth2-http to v1.12.1 ([#158](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/158)) ([17f792a](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/17f792a9edd7096fe3458567e5a5fe38f18001bd))
* Update dependency com.google.cloud:google-cloud-core to v2.8.23 ([#147](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/147)) ([2c3a7e1](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/2c3a7e198850d29bf0ef843caec472b94e285e1b))
* Update dependency com.google.cloud:google-cloud-core to v2.8.27 ([#166](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/166)) ([35935ce](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/35935cee785a01da9cf75e1598b9979163fef027))
* Update dependency com.google.cloud:google-cloud-storage to v2.13.1 ([#152](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/152)) ([e5c92ab](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/e5c92abcb305c338109701f536608e74e57b0e2a))
* Update dependency com.google.cloud:google-cloud-storage to v2.15.0 ([#168](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/168)) ([5adf9c3](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/5adf9c3cf8a60b1bbc37e7f428a24361800c64fa))
* Update dependency com.google.errorprone:error_prone_annotations to v2.16 ([#155](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/155)) ([b206cb3](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/b206cb3a744088c753a651cea7eb5c7e212549e7))
* Update dependency kr.motd.maven:os-maven-plugin to v1.7.1 ([#173](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/173)) ([a75b054](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/a75b054174a9530840094790e848b635262d8368))
* Update dependency org.threeten:threetenbp to v1.6.3 ([#156](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/156)) ([8fa6911](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/8fa6911d44351a43c4bf9fde369c38501ef9ceb4))
* Update dependency org.threeten:threetenbp to v1.6.4 ([#172](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/172)) ([9a09636](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/9a0963622933f2990f97a520faf5806296f57bc8))
* Update kafka.version to v3.3.1 ([#133](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/133)) ([b2d06a7](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/b2d06a7f82d94452fde799367be71fc09f0a66f9))
* Update protobuf-java.vesion to v3.21.8 ([#159](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/159)) ([2ee8585](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/2ee85855bb77436ed7ac4aa50b07b0c0ef2310b5))
* Update protobuf-java.vesion to v3.21.9 ([#167](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/167)) ([c5707c6](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/c5707c6c6401de81eb08561aaa004d0c5eb23161))
* Update pubsub and gax versions ([#175](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/175)) ([8afad70](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/8afad70933439f8594cf3937763ebe5f83fd5b8d))
* Update pubsublite.version to v1.8.0 ([#153](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/153)) ([205ac20](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/205ac202ce583613d328d729f77a4cd998e8d6ee))

## [0.1.2](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v0.1.1...v0.1.2) (2022-09-29)


### Bug Fixes

* Update version in pom and readme ([#140](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/140)) ([8e748f4](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/8e748f4f488219e88a397f6c20901f6ad5117168))

## [0.1.1](https://github.com/googleapis/java-pubsub-group-kafka-connector/compare/v0.1.0...v0.1.1) (2022-09-29)


### Bug Fixes

* Update groupId, parent, description in pom.xml and license headers ([#135](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/135)) ([f849afe](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/f849afeedc714d79bf86b8aa74b8683a55942eff))


### Dependencies

* Update protobuf-java.vesion to v3.21.7 ([#137](https://github.com/googleapis/java-pubsub-group-kafka-connector/issues/137)) ([dd047ae](https://github.com/googleapis/java-pubsub-group-kafka-connector/commit/dd047aefcd001300c67ee73af50f5fdd427fb8d1))

## 0.1.0 (2022-09-28)

`com.google.cloud:pubsub-group-kafka-connector` 0.1.0 release
