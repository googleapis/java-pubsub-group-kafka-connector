# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""This script is used to synthesize generated parts of this library."""

import synthtool.languages.java as java

java.common_templates(excludes=[
    # Prevent owlbot from adding snippet bot.
    ".github/snippet-bot.yml",
    # Exclude Java 17 in ci.
    ".github/workflows/ci.yaml",
    ".github/workflows/samples.yaml",
    # Use customized Kokoro build.sh.
    ".kokoro/build.sh",
    # Exclude these Kokoro build configs from owlbot updates.
    ".kokoro/continuous/java8.cfg",
    ".kokoro/nightly/integration.cfg",
    ".kokoro/nightly/java11-integration.cfg",
    ".kokoro/nightly/java11.cfg",
    ".kokoro/nightly/java7.cfg",
    ".kokoro/nightly/java8-osx.cfg",
    ".kokoro/nightly/java8-win.cfg",
    ".kokoro/nightly/java8.cfg",
    ".kokoro/nightly/samples.cfg",
    ".kokoro/presubmit/clirr.cfg",
    ".kokoro/presubmit/dependencies.cfg",
    ".kokoro/presubmit/graalvm-native-17.cfg",
    ".kokoro/presubmit/graalvm-native.cfg",
    ".kokoro/presubmit/integration.cfg",
    ".kokoro/presubmit/java11.cfg",
    ".kokoro/presubmit/java7.cfg",
    ".kokoro/presubmit/java8-osx.cfg",
    ".kokoro/presubmit/java8-win.cfg",
    ".kokoro/presubmit/java8.cfg",
    ".kokoro/presubmit/linkage-monitor.cfg",
    ".kokoro/presubmit/lint.cfg",
    ".kokoro/presubmit/samples.cfg",
    ".kokoro/readme.sh",
    # Exclude owlbot from README.
    "README.md",
    # Exclude owlbot from adding samples dir.
    "samples/install-without-bom/pom.xml",
    "samples/pom.xml",
    "samples/snapshot/pom.xml",
    "samples/snippets/pom.xml",
])
