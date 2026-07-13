ANDROID_HOME ?= /opt/homebrew/share/android-commandlinetools
NDK_HOME ?= $(ANDROID_HOME)/ndk/27.1.12297006
TOOLCHAIN = $(NDK_HOME)/toolchains/llvm/prebuilt/darwin-x86_64
CC = $(TOOLCHAIN)/bin/aarch64-linux-android24-clang
AR = $(TOOLCHAIN)/bin/llvm-ar
RANLIB = $(TOOLCHAIN)/bin/llvm-ranlib
SYSROOT = $(TOOLCHAIN)/sysroot

OPUS_DIR = libopus

OUT_DIR = ../../plugins/android-audiotrack/jniLibs/arm64-v8a
JNI_SRC = ../../plugins/android-audiotrack/libopus/opus_jni.c

.PHONY: all clean

all: $(OUT_DIR)/libopus_jni.so

$(OPUS_DIR)/config.h:
	cd $(OPUS_DIR) && ./configure \
		--host=aarch64-linux-android \
		CC=$(CC) AR=$(AR) RANLIB=$(RANLIB) \
		--with-sysroot=$(SYSROOT) \
		--disable-shared --enable-static \
		--disable-doc --disable-extra-programs --disable-intrinsics

$(OPUS_DIR)/.libs/libopus.a: $(OPUS_DIR)/config.h
	$(MAKE) -C $(OPUS_DIR) -j4

$(OUT_DIR):
	mkdir -p $(OUT_DIR)

$(OUT_DIR)/libopus_jni.so: $(OPUS_DIR)/.libs/libopus.a $(OUT_DIR) $(JNI_SRC)
	$(CC) -shared -fPIC \
		-I $(SYSROOT)/usr/include \
		-I $(OPUS_DIR)/include \
		$(JNI_SRC) \
		$(OPUS_DIR)/.libs/libopus.a \
		-lm \
		-o $@
	@echo "\nBuilt: $@"

clean:
	$(MAKE) -C $(OPUS_DIR) clean 2>/dev/null || true
	rm -f $(OUT_DIR)/libopus_jni.so
