#!/usr/bin/env python3
"""Compile, test and package with an existing Loom/Maven cache when Gradle cannot start.

This is a fallback, not a replacement for ./gradlew build or the in-game smoke test.
Requires Java 25, cached 26.3 Minecraft and its Fabric/LWJGL dependencies.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))) / "caches"
java_home = Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else None

def executable(name):
    result = str(java_home / "bin" / name) if java_home else shutil.which(name)
    if not result:
        raise SystemExit("Set JAVA_HOME to a Java 25 JDK")
    return result

minecraft = cache / "fabric-loom/26.3/minecraft-merged.jar"
if not minecraft.is_file():
    raise SystemExit("No cached Minecraft 26.3 merged JAR. Run ./gradlew build with network access first.")
jars = sorted((cache / "modules-2/files-2.1").rglob("*.jar")) + [minecraft]
classpath = os.pathsep.join(map(str, jars + [ROOT / "src/main/resources"]))
build = ROOT / "build/offline"
classes = build / "classes"
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
sources = [p for source in ["main", "client", "test", "clientTest"] for p in (ROOT / f"src/{source}/java").rglob("*.java")]
args = ["-proc:none", "-encoding", "UTF-8", "-d", str(classes), "-classpath", classpath] + list(map(str, sources))
argfile = build / "compile.args"
# Java argfiles need quoting for cache paths containing spaces.
argfile.write_text("\n".join(json.dumps(a) for a in args))
subprocess.run([executable("javac"), "@" + str(argfile)], check=True)
for test in ["AtmosphereTest", "CurvesTest", "ProjectTest", "FogTest", "WorldSignalsTest", "CommandLayersTest", "MinecraftContractTest"]:
    subprocess.run([executable("java"), "--enable-native-access=ALL-UNNAMED", "-cp", str(classes) + os.pathsep + classpath,
                    "io.github.tjhgibbs.shadermaker." + test], check=True)
subprocess.run([executable("java"), "--enable-native-access=ALL-UNNAMED", "-cp", str(classes) + os.pathsep + classpath,
                "io.github.tjhgibbs.shadermaker.client.WorldContextTest"], check=True)
subprocess.run([executable("java"), "-Djava.awt.headless=true", "-cp", str(classes) + os.pathsep + classpath,
                "io.github.tjhgibbs.shadermaker.client.SampleImageTest"], check=True)
# Optional actual raster smoke on systems with Mesa EGL (no Minecraft window required).
if any(flag in sys.argv for flag in ["--visual-gl", "--fog-gl", "--context-gl"]):
    fixtures = build / "visual-biomes"
    subprocess.run([executable("java"), "--source", "25", "-cp", str(classes) + os.pathsep + classpath,
                    str(ROOT / "tools/VisualBiomeRenderFixtures.java"), str(fixtures)], check=True)
    if "--context-gl" in sys.argv:
        context_fixtures=build / "context"
        subprocess.run([executable("java"), "--source", "25", "-cp", str(classes) + os.pathsep + classpath,
                        str(ROOT / "tools/WorldSignalRenderFixtures.java"), str(context_fixtures)], check=True)
        subprocess.run([sys.executable, str(ROOT / "tools/verify-world-signals-gl.py"),
                        str(cache / "fabric-loom/26.3/minecraft-client.jar"), str(fixtures), str(context_fixtures)], check=True)
    if "--fog-gl" in sys.argv:
        fog_fixtures = build / "fog"
        subprocess.run([executable("java"), "--source", "25", "-cp", str(classes) + os.pathsep + classpath,
                        str(ROOT / "tools/FogRenderFixtures.java"), str(fog_fixtures)], check=True)
        subprocess.run([sys.executable, str(ROOT / "tools/verify-fog-gl.py"),
                        str(cache / "fabric-loom/26.3/minecraft-client.jar"), str(fixtures), str(fog_fixtures)], check=True)
    elif "--context-gl" not in sys.argv:
        subprocess.run([sys.executable, str(ROOT / "tools/verify-visual-biomes-gl.py"),
                        str(cache / "fabric-loom/26.3/minecraft-client.jar"), str(fixtures)], check=True)
# Package only production classes, excluding test sources and all dev tooling.
production = {str(p.relative_to(ROOT / f"src/{source}/java")).removesuffix(".java") for source in ["main", "client"]
              for p in (ROOT / f"src/{source}/java").rglob("*.java")}
artifact = ROOT / "build/libs/shader-maker-0.1.0.jar"
artifact.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(artifact, "w", zipfile.ZIP_DEFLATED) as jar:
    for p in sorted(classes.rglob("*.class")):
        rel = p.relative_to(classes).as_posix()
        if rel.removesuffix(".class").split("$")[0] in production:
            jar.write(p, rel)
    for p in sorted((ROOT / "src/main/resources").rglob("*")):
        if p.is_file():
            jar.writestr(p.relative_to(ROOT / "src/main/resources").as_posix(), p.read_bytes().replace(b"${version}", b"0.1.0"))
with zipfile.ZipFile(artifact) as jar:
    metadata = json.loads(jar.read("fabric.mod.json"))
    assert metadata["version"] == "0.1.0"
    assert all("Test" not in name and "Scraper" not in name and "blockscraper" not in name for name in jar.namelist())
print("Offline artifact (runtime validation pending):", artifact)
