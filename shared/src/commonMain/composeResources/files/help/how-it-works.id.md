---
title: Teknologi di Balik Soundscape
layout: page
parent: "Menggunakan Soundscape"
has_toc: false
nav_order: 200
lang: id
permalink: /users/how-it-works.html
machine-translated: true
translated-from: 1d7f81a05233463dbc2eb8bf62ea26979f5114d0
---

# Teknologi di Balik Soundscape
Halaman ini bertujuan memberi gambaran umum tentang cara kerja aplikasi Soundscape di balik layar. Anda tidak perlu membacanya untuk menggunakan aplikasi, tetapi halaman ini ditulis karena beberapa alasan:

1. Untuk membantu pengguna baru yang tertarik memahami di mana batasan aplikasi
1. Untuk memberi pengguna gambaran tentang apa lagi yang mungkin dilakukan dengan fitur baru
1. Untuk memberi pengembang gambaran umum tentang fungsi aplikasi

Ada dua teknologi yang memungkinkan aplikasi ini: GPS dan data OpenStreetMap. GPS memberi kami gambaran yang baik tentang di mana ponsel berada dan di mana ponsel pernah berada. Data OpenStreetMap kemudian dapat digunakan untuk menemukan apa yang ada di sekitar, dan kami dapat menggunakannya untuk menjelaskan hal itu kepada pengguna.

## Suar Audio
Dari sisi teknologi, sebagian besar hal ini paling sederhana untuk diterapkan. Jika kami mengetahui lokasi ponsel dan arah ponsel, kami dapat mengubah audio suar sehingga terdengar seperti datang dari arah tersebut. Kami menggunakan pustaka dari Steam Audio untuk memosisikan audio, yang menggunakan fungsi transfer terkait kepala (HRTF) agar posisi suara terdengar sebaik mungkin. Satu-satunya hal lain yang kami lakukan adalah mengubah audio suar sehingga suara yang berbeda diputar tergantung pada sudut antara arah pengguna dan lokasi suar. Sudutnya berbeda tergantung pada suar yang dipilih (*Taktil*, *Kilat*, *Ping*, dll.) dan beberapa memiliki lebih banyak suara dibandingkan yang lain. Itulah Suar Audio pada tingkat paling sederhana.

Satu-satunya kerumitan tambahan adalah asumsi tentang lokasi ponsel dan arah yang ditunjuk pengguna. Mari kita lihat satu per satu.

### Lokasi
Lokasi yang diberikan oleh GPS bisa memiliki kesalahan yang cukup besar. Hal ini bergantung pada seberapa banyak langit yang terlihat oleh GPS ponsel, dan seberapa banyak pohon dan gedung tinggi yang memantulkan sinyal GPS dalam perjalanannya ke ponsel.

Untuk menyaring lokasi, kami menggunakan teknik yang disebut pencocokan peta (map matching). Teknik ini mengasumsikan bahwa pengguna kemungkinan besar sedang bergerak di sepanjang jalan atau jalur yang ada di peta; kami menggunakan istilah "ruas" (dalam bahasa Inggris "Way") untuk mencakup semua jalan raya, jalan setapak, dan jalur. Pencocokan peta melihat ke mana pengguna telah bergerak, lalu dengan menggunakan arah gerakan dan data peta setempat, memilih lokasi yang paling mungkin di sebuah ruas. Pendekatan ini tidak hanya memperhitungkan kesalahan GPS, tetapi juga kesalahan dalam data peta. Tidak semua ruas dipetakan dengan akurat, sehingga ruas pun memiliki kesalahan. Untuk menentukan ruas mana yang paling mungkin sedang dilalui pengguna, algoritme mempertimbangkan:
* Seberapa dekat sebuah ruas dengan lokasi GPS dan lokasi GPS sebelumnya
* Arah perjalanan: apakah pengguna bergerak ke arah yang sama dengan ruas tersebut
* Apakah mungkin berpindah dari lokasi hasil pencocokan peta terakhir ke lokasi baru melalui jaringan ruas. Hal ini diperlukan untuk mencegah perpindahan antar ruas yang sebenarnya tidak terhubung, misalnya ruas yang satu melintas di atas ruas lain melalui jembatan, atau di bawahnya melalui terowongan.
Pencocokan peta bisa memutuskan bahwa tidak ada ruas di sekitar, atau tidak yakin ruas mana yang sedang dilalui pengguna. Dalam hal ini, ia hanya menunggu lokasi GPS berikutnya dan mencoba lagi hingga yakin.

### Arah
Ada beberapa arah yang kami lacak di perangkat lunak:

1. Arah yang ditunjuk ponsel. Kami menggunakannya saat ponsel tidak terkunci dan aplikasi sedang digunakan, tetapi juga saat ponsel terkunci, asalkan dipegang mendatar dengan layar menghadap ke langit. Hal ini perlu diingat saat Anda memasukkan ponsel ke dalam tas. Jika ponsel diletakkan mendatar di dasar tas yang dipegang tegak, aplikasi akan menggunakan arah acak yang ditunjuk tas.
1. Arah pergerakan ponsel.
1. Arah dari headphone dengan pelacakan kepala. Saat ini kami tidak menggunakannya, meskipun aplikasi iOS pernah mendukungnya. Kami sudah memiliki teknologinya untuk menambahkannya di masa mendatang.

Saat ponsel terkunci dan berada di dalam tas, aplikasi akan menggunakan arah perjalanan. Namun, jika pengguna tidak bergerak, tidak ada arah yang tersedia. Jika ini terjadi, Suar Audio menjadi lebih pelan untuk menunjukkan bahwa arah suar saat ini tidak dapat diketahui: pengguna bisa saja berputar tanpa berpindah lokasi.

Untuk beberapa penggunaan arah di aplikasi, arah tersebut "disesuaikan" dengan arah ruas hasil pencocokan peta. Jadi, jika pengguna berjalan kira-kira searah dengan ruas, arah sebenarnya dari ruas tersebut dianggap benar dan digunakan dalam perhitungan tersebut.

### Kesimpulan
Meskipun sekilas Suar Audio tampak sederhana, penggunaan pencocokan peta untuk mencoba menghilangkan kesalahan lokasi dan arah menambah cukup banyak kerumitan.

## Data peta
Hampir semua data peta yang digunakan aplikasi berasal dari proyek OpenStreetMap. Kami menjalankan server yang berisi peta seluruh dunia pada beberapa tingkat zoom. Setiap tingkat zoom dibagi menjadi petak. Tingkat zoom 0 berisi 1 petak, tingkat 1 berisi 4 petak, tingkat 2 berisi 16 petak, dan seterusnya hingga tingkat 14 yang berisi sekitar 268 juta petak untuk mencakup seluruh planet. Setiap petak berisi beberapa lapisan dan setiap lapisan memiliki titik, garis, dan poligon yang dapat digambar untuk membuat peta grafis. Peta grafis itulah yang ditampilkan kepada pengguna di layar aplikasi. Setiap titik, garis, dan poligon memiliki metadata yang menjelaskan apa benda tersebut. Sebagian besar langsung berasal dari data OpenStreetMap, sehingga sebuah garis bisa berupa `footway` yang merupakan `sidewalk`, atau `road` yang berupa `minor`.

Data diubah menjadi peta grafis melalui sebuah "gaya" yang memiliki aturan tentang cara menggambar berbagai titik, garis, dan poligon di setiap lapisan, misalnya cara menggambar jalan setapak, hutan, atau halte bus. Aturan ini bisa berbeda tergantung tingkat zoom, itulah sebabnya saat Anda memperbesar, semakin banyak titik dan garis yang muncul yang tidak terlihat saat diperkecil, misalnya halte bus dan jalan setapak.

Dengan mengubah gaya, kami dapat mengubah tampilan peta di layar, dan dari situlah "peta aksesibel" yang sedang kami uji coba berasal. Peta ini bertujuan memiliki kontras lebih tinggi serta garis dan teks yang lebih tebal. Gaya sudah terpasang di dalam aplikasi, jadi kami tidak perlu mengubah peta di server untuk mengubah tampilannya.

Namun, bagaimana kami menggunakan data peta untuk audio?

### Menggunakan data peta untuk audio
Saat ini kami menggunakan sebagian kecil data peta untuk membuat antarmuka audio. Hampir seluruh antarmuka audio hanya menggunakan petak pada tingkat zoom tertinggi. Aplikasi menggabungkan kisi 2 x 2 petak di sekitar posisi pengguna, lalu hanya melihat beberapa lapisan:

* `transportation` - untuk semua jenis ruas, termasuk jalan raya, jalan setapak, rel kereta api, dan rel trem.
* `poi` - tempat menarik, misalnya toko, pusat olahraga, bangku, kotak pos, halte bus, dll.
* `building` - untuk `poi` yang dipetakan lebih dari sekadar titik, misalnya supermarket besar atau balai kota.

Aplikasi menyambungkan garis dan poligon melintasi batas petak dan mengubah semua ruas menjadi potongan ruas dan persimpangan yang saling terhubung. Hal ini penting karena memungkinkan kami menelusuri sebuah ruas untuk mengetahui ke mana kami bisa sampai.

Semua data yang telah diolah juga disimpan dalam format yang mudah dicari sehingga aplikasi dapat dengan mudah menemukan fitur peta mana yang ada di sekitar. Pada tahap ini, data diklasifikasikan ke dalam kategori. Kategori saat ini adalah:

* Jalan raya
* Jalan raya dan jalan setapak (semua ruas)
* Persimpangan: titik tempat ruas-ruas bersilangan
* Pintu masuk: titik pada bangunan yang ditandai sebagai pintu masuk.
* Penyeberangan: tempat menyeberang jalan
* Tempat menarik: semua tempat menarik
* Perhentian angkutan umum: halte bus, stasiun kereta, perhentian trem, dan sebagainya.
* Subkategori tempat menarik:
  * Informasi
  * Objek
  * Tempat
  * Landmark
  * Mobilitas
  * Keselamatan
* Permukiman dan subkategorinya (lihat bagian berikutnya)
  * Kota besar
  * Kota
  * Desa
  * Dusun

Dengan ini, untuk lokasi mana pun aplikasi dapat dengan mudah menemukan

* "Semua perhentian angkutan umum dalam radius 50 m" atau
 * "Persimpangan terdekat di depan saya" atau
 * "Dusun, desa, kota, atau kota besar terdekat"

Dengan ini, membuat pemberitahuan hanya soal mencari data berdasarkan lokasi dan arah saat ini. Saat pengguna bergerak melintasi kisi petak, aplikasi memperbaruinya agar selalu berpusat di sekitar lokasi saat ini.

### Lebih banyak data
Salah satu masalah dengan kisi data peta kami yang sangat lokal adalah kami hanya dapat "melihat" paling jauh sekitar 1 km ke segala arah. Itu cukup untuk menjelaskan apa yang ada di depan kami, tetapi terkadang kami ingin memberi lebih banyak konteks. Contoh utamanya adalah saat aplikasi digunakan dan pengguna tidak sedang berjalan kaki.

Saat aplikasi mendeteksi bahwa pengguna bergerak lebih cepat dari 5 meter per detik, aplikasi mengubah cara ia menjelaskan dunia. Alih-alih mengumumkan setiap persimpangan dan tempat menarik, aplikasi lebih jarang memberi pemberitahuan dan hanya tentang jalan di sekitar. Masalahnya, mengetahui nama jalan tidak terlalu berguna jika Anda tidak tahu di kota mana jalan itu berada.

Untuk mengatasinya, kami kini juga mengolah data peta pada tingkat zoom yang lebih rendah dan mengambil data dari lapisan `place`. Lapisan ini berisi nama kota, kota besar, lingkungan, desa, dan sebagainya. Salah satu masalah dalam pemetaan adalah tidak selalu ada batas yang jelas antara tempat-tempat ini. OpenStreetMap terkadang memiliki batas kota di basis datanya, tetapi meskipun demikian, informasi tersebut sering hilang saat sampai ke peta berpetak kami. Yang kami miliki adalah lokasi tempat nama-nama tempat digambar di peta. Nama-nama ini dikategorikan, lalu aplikasi akan menemukan dusun, desa, kota, atau kota besar yang terdekat dengan pengguna dan mengumumkannya.

Untuk banyak kota besar, nama kotanya sendiri tidak akan pernah diumumkan, karena sebagian besar kota besar dibagi menjadi bagian yang lebih kecil seperti lingkungan, tetapi bagian ini memberi konteks tambahan dan sangat berguna. Ingat saja bahwa saat aplikasi melaporkan bahwa Anda berada di dekat jalan di lingkungan tertentu, itu hanya berarti label lingkungan tersebut adalah titik terdekat, dan bisa saja keliru atau bahkan berada di seberang sungai.

### Lebih banyak konteks
Semakin banyak konteks yang dapat ditambahkan ke dalam deskripsi, semakin baik, asalkan tetap ringkas dan mudah ditebak. Salah satu masalah yang kami temukan saat menjelaskan persimpangan adalah sering kali ada ruas "tanpa nama". Ini adalah ruas yang tidak memiliki nama. Dalam data peta, ruas ini mungkin hanya jalan tanah, jalan setapak, atau jalan layanan, tetapi tanpa konteks tambahan, ruas ini tidak terlalu berguna dalam deskripsi. Untungnya, kami bisa melakukan yang lebih baik: setiap kali aplikasi akan mengumumkan ruas tanpa nama, aplikasi mencoba mencari konteks tambahan untuknya.

* **Apakah ini trotoar?**
Banyak area di OpenStreetMap kini memetakan trotoar terpisah dari jalan. Trotoar ini biasanya diberi tag `sidewalk`, tetapi biasanya tidak menyebutkan jalan mana yang menjadi pasangannya.

Saat aplikasi menemukan trotoar tanpa nama, aplikasi mencari jalan yang dianggapnya berada di sebelahnya dan menggunakan nama jalan itu untuk menamai trotoar. Ternyata hal ini sangat penting untuk pemberitahuan kami. Alih-alih mengumumkan setiap persimpangan trotoar, saat kami bergerak di sepanjang trotoar, pemberitahuan dibuat seolah-olah kami bergerak di sepanjang jalan yang terkait. Alih-alih *"Bergerak ke barat di jalan setapak"* kami mendengar *"Bergerak ke barat di Moor Road"*. Pengguna berada di trotoar yang dipetakan, tetapi deskripsinya lebih masuk akal.

* **Apakah ruas ini berakhir di ruas yang bernama?**
Sangat sering ada jalan setapak pejalan kaki yang menghubungkan dua jalan. Dengan melihat kedua ujung jalan setapak, kami dapat dengan mudah menambahkan konteks tersebut sehingga dari satu arah bisa menjadi *"Jalan setapak menuju Moor Road"* dan dari ujung lainnya *"Jalan setapak menuju Roselea Drive"*. Hal ini hanya dilakukan jika jalan setapak tidak bercabang; jika bercabang menjadi dua jalan setapak tanpa nama, kami tidak mencoba menambahkan konteks ini.

* **Apakah ruas ini berakhir di dekat Penanda?**
Jika ruas tanpa nama berawal atau berakhir di dekat Penanda, Penanda itu digunakan untuk menjelaskannya, misalnya *"Jalan setapak menuju persimpangan pohon besar"*. Pengguna dapat menambahkan Penanda di mana saja, dan dengan menambahkan Penanda di sepanjang jaringan jalan setapak, pengguna dapat memberi konteks pada seluruh rute.

* **Apakah ruas ini masuk ke atau keluar dari tempat menarik?**
Jika ruas tanpa nama berawal di luar tempat menarik dan berakhir di dalamnya (atau sebaliknya), kami dapat menambahkan konteks tersebut, misalnya *"Jalan tanah menuju Lennox Park"*.

* **Apakah ruas ini berakhir di dekat pintu masuk?**
Jika ruas tanpa nama berawal atau berakhir lebih dekat ke pintu masuk, kami dapat menambahkan konteks tersebut, misalnya *"Jalan layanan menuju Best Buy"*.

* **Apakah ruas ini berakhir di dekat landmark atau tempat?**
Jika ruas tanpa nama berawal atau berakhir lebih dekat ke landmark, kami juga dapat menambahkan konteks tersebut, misalnya *"Jalan layanan menuju Katedral St. Giles"*.

* **Apakah ini jalan buntu?**
Aplikasi menandai sebagai jalan buntu setiap ruas tanpa nama yang tidak menuju ke mana pun.

* **Apakah ruas ini melewati jembatan, terowongan, atau tangga?**
Jika ruas tanpa nama melewati jembatan, terowongan, atau naik/turun tangga, hal ini dicatat dan ditambahkan ke konteks. Hal ini terpisah dari penandaan tujuan, sehingga konteks seperti *"Jalan setapak melewati jembatan menuju Lennox Park"* dimungkinkan.

Konteks ini ditambahkan secara berurutan, sehingga dari satu arah bisa jadi *"Jalan setapak menuju Park Lane melalui tangga"* dan dari arah lain *"Jalan setapak menuju Lennox Park melalui tangga"*. Jalan yang bernama diprioritaskan saat meninggalkan taman, tetapi taman digunakan saat memasukinya.

#### Konteks di masa mendatang
Ada berbagai konteks tambahan yang kami harap dapat ditambahkan di masa mendatang, termasuk:
* Konteks untuk ruas yang mengikuti aliran air, misalnya *"Jalan setapak di sebelah Sungai Dee"*
* Konteks untuk ruas yang mengikuti tepi perairan, misalnya *"Jalan setapak di sebelah waduk Milngavie"*
* Konteks untuk ruas yang mengikuti rel kereta api, misalnya *"Jalan setapak di sebelah rel kereta api"*. Ini bahkan bisa menyertakan nama jalur kereta apinya
* Menambahkan isi tambahan untuk jembatan dan terowongan, yaitu apa yang dilewati di atas atau di bawahnya, misalnya *"Jalan setapak melalui jembatan di atas rel kereta api menuju Moor Road"*

## Pemberitahuan audio
Sekarang setelah data peta berada dalam format yang mudah digunakan, membuat pemberitahuan benar-benar cukup sederhana.

### Pemberitahuan saat berjalan kaki
Saat berjalan kaki, pemberitahuan yang dapat terjadi adalah (sesuai urutan prioritas):

1. Menjelaskan seberapa jauh tujuan saat ini
1. Menjelaskan persimpangan yang akan datang
1. Menjelaskan 5 tempat menarik terdekat

Semua pemberitahuan dibatasi frekuensinya agar tidak terlalu sering berulang. Jika pengguna berhenti bergerak, pemberitahuan akan berhenti, dan bahkan saat bergerak, pemberitahuan tidak akan berulang di setiap lokasi GPS baru. Frekuensinya, sama seperti aplikasi iOS, adalah:

* Setiap 60 detik untuk tujuan saat ini
* Setiap 30 detik untuk persimpangan yang akan datang
* Setiap 60 detik untuk tempat menarik

Pemberitahuan dapat disaring melalui menu *Pengaturan*, dan tentu ada ruang untuk memperluas perilaku ini.

### Pemberitahuan saat bergerak lebih cepat
Saat bergerak lebih cepat dari 5 meter per detik, pemberitahuan untuk tujuan saat ini tetap berjalan, tetapi pemberitahuan persimpangan dan tempat menarik digantikan oleh pemberitahuan yang menjelaskan secara kasar di mana pengguna berada. Pemberitahuan ini menyebutkan perhentian angkutan umum terdekat, tempat menarik tempat kami berada (misalnya di dalam taman yang besar), atau jalan dan permukiman terdekat. Pemberitahuan ini menggunakan data yang dijelaskan sebelumnya, dan jelas ada ruang untuk memungkinkan penyesuaian di masa mendatang.

## Penanda dan Rute
Sebagian besar, Penanda dan Rute hanyalah fitur antarmuka pengguna yang tidak bergantung pada GPS maupun data peta. Penanda adalah lokasi bernama yang ingin disimpan pengguna, dan Rute adalah daftar berurutan dari Penanda tersebut. Antarmuka pengguna untuk membuat keduanya diambil langsung dari versi iOS.

### Memutar rute
Saat diputar, rute menjadi hidup. Saat sebuah rute diputar, Suar Audio dibuat di Penanda pertama dalam rute. Setelah pengguna mendekati Penanda tersebut, rute secara otomatis memindahkan Suar Audio ke Penanda berikutnya. Jika tidak ada Penanda lagi, pemutaran rute selesai.

## Kesimpulan
Semoga halaman ini memberi gambaran tentang cara kerja aplikasi. Aplikasi ini terus berkembang berdasarkan masukan pengguna, jadi silakan hubungi kami jika menurut Anda ada sesuatu yang bisa ditambahkan.
