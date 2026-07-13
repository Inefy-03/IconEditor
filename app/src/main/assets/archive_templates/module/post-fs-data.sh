MODDIR="${0%/*}"
for i in /data/user/0/com.xiaomi.market/files/download_icon /data/user/0/com.xiaomi.market/cache/icons; do
  if [ -d $i ]; then
    rm -rf $i/*
    chattr +i $i
  fi
done
