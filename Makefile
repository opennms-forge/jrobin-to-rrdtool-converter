.DEFAULT_GOAL := jrobin-to-rrdtool

SHELL               := /bin/bash -o nounset -o pipefail -o errexit
VERSION             ?= $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
GIT_BRANCH          := $(shell git branch --show-current)
GIT_SHORT_HASH      := $(shell git rev-parse --short HEAD)
DATE                := $(shell date -u +"%Y-%m-%dT%H:%M:%SZ") # Date format RFC3339
JAVA_MAJOR_VERSION  := 17

ARTIFACTS_DIR       := ./target/artifacts
RELEASE_VERSION     := UNSET.0.0
RELEASE_BRANCH      := main
MAJOR_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f1)
MINOR_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f2)
PATCH_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f3)
SNAPSHOT_VERSION    := $(MAJOR_VERSION).$(MINOR_VERSION).$(shell expr $(PATCH_VERSION) + 1)-SNAPSHOT
RELEASE_LOG         := $(ARTIFACTS_DIR)/release.log
OK                  := "[ 👍 ]"
CONTAINER_TAG       := docker.io/opennms/jrb2rrd:${VERSION}

.PHONY: help
help:
	@echo ""
	@echo "Build JRobin to RRDTool converter from source"
	@echo "Goals:"
	@echo "  help:              Show this help with explaining the build goals"
	@echo "  jrobin-to-rrdtool: Compile and create a runnable jar from source"
	@echo "  clean:             Clean the build artifacts"
	@echo "  release:           Create a release in the local repository, e.g. make release RELEASE_VERSION=x.y.z"
	@echo ""

.PHONY: deps-build
deps-build:
	@echo -n "👮‍♀️ Create artifact directory:   "
	@mkdir -p $(ARTIFACTS_DIR)
	@echo $(OK)
	@echo -n "👮‍♀️ Check Java runtime:          "
	@command -v java > /dev/null
	@echo $(OK)
	@echo -n "👮‍♀️ Check Java compiler:         "
	@command -v javac > /dev/null
	@echo $(OK)
	@echo -n "👮‍♀️ Check Maven binary:          "
	@command -v mvn > /dev/null
	@echo $(OK)
	@echo -n "👮‍♀️ Check Java version $(JAVA_MAJOR_VERSION):       "
	@java --version | grep '$(JAVA_MAJOR_VERSION)\.[[:digit:]]*\.[[:digit:]]*' >/dev/null
	@echo $(OK)
	@echo -n "👮‍♀️ Validate Maven project:      "
	@mvn validate > /dev/null
	@echo $(OK)

.PHONY: deps-docker
deps-docker:
	@echo -n "👮‍♀️ Check Docker exists:         "
	@command -v docker > /dev/null
	@echo $(OK)

.PHONY: jrobin-to-rrdtool
jrobin-to-rrdtool: deps-build
	mvn install assembly:single

.PHONY: clean
clean: deps-build
	mvn clean

.PHONY: collect-artifacts
collect-artifacts:
	find . -type f -regex ".*\/target\/convertjrb-$(VERSION)-jar-with-dependencies\.jar" -exec cp {} $(ARTIFACTS_DIR) \;
	echo $(VERSION) > $(ARTIFACTS_DIR)/pom-version.txt
	shasum -a 256 -b $(ARTIFACTS_DIR)/convertjrb-$(VERSION)-jar-with-dependencies.jar > $(ARTIFACTS_DIR)/shasum256.txt
	cd $(ARTIFACTS_DIR); tar czf convertjrb-$(VERSION).tar.gz convertjrb-$(VERSION)-jar-with-dependencies.jar shasum256.txt
	shasum -a 256 -b $(ARTIFACTS_DIR)/convertjrb-$(VERSION).tar.gz > $(ARTIFACTS_DIR)/convertjrb-$(VERSION).sha256

.PHONY: oci
oci: deps-docker
	docker build -t $(CONTAINER_TAG) .

.PHONY: release
release: deps-build
	@mkdir -p target
	@echo ""
	@echo "Release version:                $(RELEASE_VERSION)"
	@echo "New snapshot version:           $(SNAPSHOT_VERSION)"
	@echo "Git version tag:                v$(RELEASE_VERSION)"
	@echo "Current branch:                 $(GIT_BRANCH)"
	@echo "Release branch:                 $(RELEASE_BRANCH)"
	@echo "Release log file:               $(RELEASE_LOG)"
	@echo ""
	@echo -n "👮‍♀️ Check release branch:        "
	@if [ "$(GIT_BRANCH)" != "$(RELEASE_BRANCH)" ]; then echo "Releases are made from the $(RELEASE_BRANCH) branch, your branch is $(GIT_BRANCH)."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮‍♀️ Check uncommited changes     "
	@if git status --porcelain | grep -q .; then echo "There are uncommited changes in your repository."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮‍♀️ Check branch in sync         "
	@if [ "$(git rev-parse HEAD)" != "$(git rev-parse @{u})" ]; then echo "$(RELEASE_BRANCH) branch not in sync with remote origin."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮‍♀️ Check release version:       "
	@if [ "$(RELEASE_VERSION)" = "UNSET.0.0" ]; then echo "Set a release version, e.g. make release RELEASE_VERSION=1.0.0"; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮‍♀️ Check version tag available: "
	@if git rev-parse v$(RELEASE_VERSION) >$(RELEASE_LOG) 2>&1; then echo "Tag v$(RELEASE_VERSION) already exists"; exit 1; fi
	@echo "$(OK)"
	@echo -n "💅 Set Maven release version:   "
	@mvn versions:set -DnewVersion=$(RELEASE_VERSION) >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "👮‍♀️ Validate build:              "
	@$(MAKE) jrobin-to-rrdtool >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "🎁 Git commit new release       "
	@git commit --signoff -am "release: JRobin to RRDTool converter $(RELEASE_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "🦄 Set Git version tag:         "
	@git tag -a "v$(RELEASE_VERSION)" -m "Release JRobin to RRDTool converter version $(RELEASE_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "⬆️ Set Maven snapshot version:  "
	@mvn versions:set -DnewVersion=$(SNAPSHOT_VERSION) >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "🎁 Git commit snapshot release: "
	@git commit --signoff -am "release: JRobin to RRDTool converter version $(SNAPSHOT_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo ""
	@echo "🦄 Congratulations! ✨"
	@echo "You made a release in your local repository."
	@echo "Publish the release by pushing the version tag"
	@echo "and the new snapshot version to the remote repo"
	@echo "with the following commands:"
	@echo ""
	@echo "  git push"
	@echo "  git push origin v$(RELEASE_VERSION)"
	@echo ""
	@echo "Thank you for computing with us."
	@echo ""
