ffmpeg -r 2 -f image2 -vcodec rawvideo -pix_fmt yuv420p -s 768x432 -i '%03d.yuv' -r 2 out.webm
