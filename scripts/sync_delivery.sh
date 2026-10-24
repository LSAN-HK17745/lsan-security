#!/bin/bash
# ============================================================
# LSAN 交付同步脚本
#   施工器 -> /mnt/game-ssd/LSAN安全
#   成品   -> /mnt/data-m2/桌面/LSAN安全/手机安全
#
# 前置条件（需在宿主机执行，因为这两个盘当前以只读方式挂进容器）：
#   sudo mount -o remount,rw /mnt/game-ssd
#   sudo mount -o remount,rw /mnt/data-m2
# ============================================================
set -e

SRC="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DEST="/mnt/game-ssd/LSAN安全"
OUT_DEST="/mnt/data-m2/桌面/LSAN安全/手机安全"

echo "源目录: $SRC"

# 容器内自动把目标盘切到可写（宿主机 remount 不会传播进已有 namespace）
ensure_rw() {
  local dev="$1" mnt="$2" major="$3" minor="$4"
  [ -w "$mnt" ] && return 0
  [ -e "$dev" ] || mknod "$dev" b "$major" "$minor" 2>/dev/null
  mount -o remount,rw "$dev" "$mnt" 2>/dev/null
  [ -w "$mnt" ]
}
ensure_rw /dev/sda1 /mnt/game-ssd 8 1 || true
ensure_rw /dev/nvme2n1p1 /mnt/data-m2 259 1 || true

mkdir -p "$WORK_DEST" 2>/dev/null
if [ ! -w "$WORK_DEST" ]; then
  echo "!! $WORK_DEST 不可写（/mnt/game-ssd 仍是只读挂载）"
  echo "   请先在宿主机执行: sudo mount -o remount,rw /mnt/game-ssd"
  exit 1
fi

echo "== 1/3 同步施工器 =="
rsync -a --delete \
  --exclude '.git' --exclude '.toolchain' --exclude '.gradle' \
  --exclude 'build' --exclude 'app/build' --exclude '.shots' --exclude 'keystore' \
  "$SRC/" "$WORK_DEST/"

echo "== 2/3 安置成品 =="
mkdir -p "$OUT_DEST"
cp -f "$SRC/dist/"*.apk "$OUT_DEST/" 2>/dev/null || true
cp -f "$SRC/dist/SHA256SUMS.txt" "$OUT_DEST/" 2>/dev/null || true
cp -f "$SRC/README.md" "$OUT_DEST/" 2>/dev/null || true
cp -f "$SRC/docs/TEST_REPORT.md" "$OUT_DEST/" 2>/dev/null || true

echo "== 3/3 完成 =="
ls -la "$OUT_DEST"
echo
echo "施工器: $WORK_DEST"
echo "成品:   $OUT_DEST"
