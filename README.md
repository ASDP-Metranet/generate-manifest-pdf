# Manifest PDF Service — Java 21 + Spring Boot 4.1.1

Menerima JSON manifest kendaraan/penumpang, membuat PDF A4 portrait berisi daftar penumpang pada kendaraan, daftar penumpang pejalan kaki, daftar barang curah dan hewan, serta rekapitulasi, dan membubuhkan QR berisi teks `id_boarding`, `approved_by_operator`, `tanggal_approve_manifest`, `id_pkk` ketika `id_pkk` tersedia, lalu **menyimpan PDF ke storage lokal** dan mengembalikan **URL acak**. Saat URL dibuka, browser menampilkan formulir password. Password-nya adalah tanggal **`document.cutOffId`** yang ditulis dengan format **DDMMYYYY** (cutOffId `20261225` => password `25122026`). Setelah password benar, PDF muncul di browser.

**PENTING:** Password berupa tanggal (cutOffId) mudah ditebak. Random URL menyulitkan penemuan dokumen tetapi tidak menggantikan autentikasi. Untuk manifest yang memuat identitas pribadi, batasi akses melalui HTTPS, autentikasi API, firewall, logging akses, kebijakan retensi file, dan bila mungkin gunakan password independen yang kuat. Contoh ini tidak mengenkripsi PDF di storage dan tidak menggunakan PDF document password; proteksi berada pada endpoint HTML sebelum file diberikan.

## 1. Struktur project

```text
manifest-pdf-springboot/
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .dockerignore
├── pom.xml
├── README.md
├── examples/
│   ├── request.json
│   ├── qr-content.txt
│   ├── call-api.sh
│   └── call-api.ps1
├── storage/                      # dibuat saat deployment (jangan di-expose publik)
└── src/
    ├── main/java/id/co/example/manifest/
    │   ├── ManifestPdfApplication.java
    │   ├── api/ManifestPdfController.java
    │   ├── dto/ManifestRequest.java
    │   ├── security/DatePasswordGate.java
    │   ├── service/ManifestPdfService.java
    │   └── storage/LocalPdfStorage.java
    └── test/java/id/co/example/manifest/
        ├── security/DatePasswordGateTest.java
        ├── service/ManifestPdfServiceTest.java
        └── storage/LocalPdfStorageTest.java
```

## 2. Deploy di Linux Server dengan Docker

Kebutuhan: Docker Engine dan Docker Compose plugin, port aplikasi/reverse proxy dapat diakses dari client.

```bash
unzip manifest-pdf-springboot-storage.zip
cd manifest-pdf-springboot
cp .env.example .env

# Direktori host untuk PDF (persisten bahkan container dibuang)
mkdir -p storage
sudo chown 10001:10001 storage
sudo chmod 700 storage

# Edit APP_PUBLIC_BASE_URL untuk domain/IP server Anda
nano .env

docker compose build --pull
docker compose up -d
docker compose ps
docker compose logs -f manifest-pdf
```

Contoh `.env` untuk domain dengan Nginx HTTPS di server yang sama:

```dotenv
APP_BIND_IP=127.0.0.1
APP_PORT=8080
APP_PUBLIC_BASE_URL=https://pdf.example.com
MANIFEST_ACCESS_ZONE=Asia/Jakarta
MANIFEST_API_USERNAME=ganti-username
MANIFEST_API_PASSWORD_HASH='$2y$12$...hash-bcrypt...'
MANIFEST_API_AUTHORITIES=MANIFEST_GENERATE,MANIFEST_CHECK
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70.0 -Duser.timezone=Asia/Jakarta -Dfile.encoding=UTF-8
```

`MANIFEST_API_USERNAME`, `MANIFEST_API_PASSWORD_HASH`, dan `MANIFEST_API_AUTHORITIES` wajib diisi; tanpa ketiganya aplikasi menolak start. Cara membuat hash ada di bagian **9. Autentikasi**. Jangan commit nilainya ke Git (`.env` sudah di-ignore).

Nginx contoh (gunakan sertifikat HTTPS yang valid):

```nginx
server {
    listen 443 ssl;
    server_name pdf.example.com;
    ssl_certificate /etc/letsencrypt/live/pdf.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/pdf.example.com/privkey.pem;
    client_max_body_size 2m;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Jika memakai IP publik langsung untuk sementara (gunakan firewall; **tidak disarankan untuk data sensitif tanpa HTTPS**):

```dotenv
APP_BIND_IP=0.0.0.0
APP_PORT=8080
APP_PUBLIC_BASE_URL=http://IP_SERVER:8080
```

Lalu `docker compose up -d --force-recreate`.

**Catatan:** `APP_PUBLIC_BASE_URL` adalah alamat yang masuk ke response JSON, **bukan** alamat bind Docker. Jangan memasukkan path `/api/...` ke variabel tersebut. Domain harus benar-benar mengarah ke aplikasi.

## 3. Generate dokumen (response sekarang URL, bukan PDF binary)

**POST** `http://localhost:8080/api/v1/manifests/generate-pdf`

Headers:

```http
Authorization: Basic base64(username:password)
Content-Type: application/json
Accept: application/json
```

Endpoint ini memakai HTTP Basic Auth dan membutuhkan authority `MANIFEST_GENERATE` (lihat bagian **9. Autentikasi**). Endpoint `/files/...` tidak memakai Basic Auth karena dilindungi password cutOffId.

Body (file lengkap: `examples/request.json`):

```json
{
  "document": {
    "cutOffId": "20261029143733008048400",
    "manifestId": "MNF-20261029-001",
    "tanggal": "2026-10-29",
    "namaKapal": "KMP. PORA PORA",
    "namaOperator": "DHARMA LAUTAN UTAMA",
    "id_boarding": "B22608190999",
    "approved_by_operator": "Dharma Lautan Utama",
    "tanggal_approve_manifest": "2026-10-29T17:17:10",
    "id_pkk": "PKK123213ABC",
    "dermaga": "Dermaga 1",
    "waktuTiba": null,
    "waktuBerangkat": "22:24"
  },
  "vehicles": [
    {
      "nomorKendaraan": "N1234QA",
      "golongan": "IV - PENUMPANG",
      "passengers": [
        {
          "nama": "MUHAMMAD ABDILLAH", "jenisKelamin": "L", "usia": 25,
          "alamatDomisili": "KOTA BOGOR",
          "nomorIdentitas": "3271041303010018", "kategori": "PENUMPANG DEWASA"
        }
      ]
    }
  ],
  "pedestrians": [
    {
      "nama": "SITI AMINAH", "jenisKelamin": "P", "usia": 28,
      "alamatDomisili": "KOTA CILEGON",
      "nomorIdentitas": "3672010101980001", "kategori": "PENUMPANG DEWASA"
    }
  ],
  "cargo": [
    {
      "golongan": "HEWAN", "nama": "SAPI", "alamatDomisili": "KOTA BOGOR",
      "nomorIdentitas": "3271041303010018", "qty": 3
    }
  ],
  "signature": {
    "atasNama": "DHARMA LAUTAN UTAMA",
    "jabatan": "Captain Kapal",
    "namaKapal": "KMP. PORA PORA",
    "qrVerificationId": "MNF-20261029-001"
  }
}
```

Aturan field:

- `vehicles`, `pedestrians`, dan `cargo` opsional dan boleh kosong semua (`[]` atau tidak dikirim). Daftar yang kosong tetap ditampilkan sebagai tabel berisi header saja, dan rekap menghitungnya sebagai 0.
- `passengers[].kategori` dan `pedestrians[].kategori` opsional. Nilainya tampil di kolom Golongan dan menjadi kelompok pada rekap. Jika kosong, kolom Golongan memakai golongan kendaraan dan rekap menghitungnya sebagai Penumpang Dewasa.
- `document.dermaga`, `document.waktuTiba`, dan `document.waktuBerangkat` opsional dan hanya tampil pada rekap.
- `document.id_pkk` opsional. Jika field tidak dikirim, bernilai `null`, atau berupa string kosong, PDF tetap dibuat tetapi tanpa QR Code.

Angka pada rekap dihitung oleh server dari data request. Jumlah Hewan dan Curah adalah total `cargo[].qty`, dan Jumlah Penumpang adalah pejalan kaki ditambah penumpang dalam kendaraan.

Response HTTP **201** (contoh token berbeda pada setiap request):

```json
{
  "fileId": "pNOh-uCNnEluOQC4vFczgWbSUKkfeCKFRPJzz0Q_0vU",
  "url": "https://pdf.example.com/api/v1/manifests/files/pNOh-uCNnEluOQC4vFczgWbSUKkfeCKFRPJzz0Q_0vU",
  "createdAt": "2026-10-23T18:07:00+07:00",
  "passwordFormat": "DDMMYYYY"
}
```

Perintah cURL:

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/manifests/generate-pdf \
  -u "$API_USER:$API_PASSWORD" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  --data-binary @examples/request.json
```

Atau jalankan `bash examples/call-api.sh` (Linux/macOS), `./examples/call-api.ps1` (PowerShell). Kedua skrip membaca `MANIFEST_API_USERNAME` dan `MANIFEST_API_PASSWORD` (password asli milik client, bukan hash) dari environment di komputer client.

## 4. Buka URL dengan password

- **GET** `/api/v1/manifests/files/{fileId}`: menampilkan halaman formulir password (HTML).
- **POST** `/api/v1/manifests/files/{fileId}/unlock`: menerima field HTML form `password`; jika benar, menjawab **303** ke `GET /api/v1/manifests/files/{fileId}/pdf?ticket=...`.
- **GET** `/api/v1/manifests/files/{fileId}/pdf?ticket=...`: memberikan `application/pdf` dengan `Content-Disposition: inline; filename="manifest-penumpang.pdf"`. Karena PDF diambil lewat GET, ikon download di viewer PDF Chrome/Edge bisa mengambilnya ulang. Ticket acak, hanya untuk file itu, berlaku 10 menit (disimpan di memori). Ticket salah atau kedaluwarsa: **303** kembali ke halaman password.
- Password adalah tanggal **`document.cutOffId`** (tanggal cutoff/keberangkatan) dari request generate, ditulis dengan format **DDMMYYYY**. Hanya **8 digit awal** cutOffId (YYYYMMDD) yang dipakai; sisanya diabaikan. Contoh: `"cutOffId": "20261029143733008048400"` => tanggal 20261029 => password **29102026**. Password ini berlaku selamanya untuk PDF tersebut, tidak berubah setiap hari.
- Server tidak menyimpan password-nya, hanya hash SHA-256 di file `<fileId>.key` di sebelah PDF. PDF lama yang dibuat sebelum fitur cutOffId (tanpa file `.key`) tidak bisa dibuka lagi dan dijawab **404**.
- Salah password: HTTP **401** + form peringatan. Setelah 5 percobaan salah: **429** dan dikunci 15 menit (status kunci disimpan di RAM, bukan persisten).
- Token tidak valid atau file tidak ada: HTTP **404**.
- Browser: salin field `url` dari response dan buka URL; ketik tanggal cutoff/keberangkatan (DDMMYYYY); klik **Buka PDF**.

Untuk mengunduh memakai command line:

```bash
# Ganti FILE_ID dari response API
FILE_ID='pNOh-uCNnEluOQC4vFczgWbSUKkfeCKFRPJzz0Q_0vU'
PASSWORD='29102026'   # 8 digit awal cutOffId (20261029) ditulis DDMMYYYY

curl -fsS -L "http://127.0.0.1:8080/api/v1/manifests/files/$FILE_ID/unlock" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "password=$PASSWORD" \
  --output manifest-penumpang.pdf
```

**Catatan:** Mengakses GET link dengan cURL hanya menerima HTML password, bukan file PDF. File PDF hanya dikirim setelah POST unlock valid. Pakai `-L` agar curl mengikuti redirect 303, dan jangan pakai `-X POST` (curl akan tetap memakai POST setelah redirect dan mendapat 405).

## 5. Penyimpanan file di host

Docker Compose me-mount folder lokal `./storage` di server ke `/app/storage` dalam container.

Nama file berupa 43 karakter URL-safe acak, contoh:

```text
storage/
  pNOh-uCNnEluOQC4vFczgWbSUKkfeCKFRPJzz0Q_0vU.pdf
```

- File tetap ada setelah `docker compose down`, `up`, `rebuild` dan restart.
- Tidak ada endpoint static download atau daftar file.
- Default permission file `600` dan folder `700` pada Linux (pemilik UID 10001).
- **Backup/retensi:** file tidak otomatis dihapus; atur jadwal pembersihan sesuai kebijakan penyimpanan, misalnya 30 hari, serta backup terenkripsi jika diperlukan.
- Jangan taruh folder storage di web root Nginx, jangan commit ke Git, dan jangan share publik.

## 6. QR Code pada PDF

Jika `document.id_pkk` berisi nilai, QR Code memuat empat field dari request
(`document`) sebagai teks biasa, satu baris per field (bukan JSON, bukan random URL).
Hasil scan QR (contoh `examples/qr-content.txt`):

```text
id_boarding: B22608190999
approved_by_operator: Dharma Lautan Utama
tanggal_approve_manifest: 2026/08/17 17:17:10
id_pkk: PKK123213ABC
```

`tanggal_approve_manifest` ditulis dengan format `yyyy/MM/dd HH:mm:ss`. Antar baris dipisah karakter baris baru (`\n`).

Tanda tangan dan QR Code berada di bagian bawah tabel rekapitulasi. Jika `document.id_pkk` tidak dikirim, `null`, atau string kosong, bagian tanda tangan
tetap ditampilkan tetapi QR Code tidak dibuat.

## 7. Operasional & troubleshooting

```bash
docker compose ps
docker compose logs --tail=100 manifest-pdf
docker compose restart manifest-pdf
# Upgrade source code
docker compose build --pull && docker compose up -d
# Lihat file yang telah disimpan di host
sudo ls -lah storage/
```

Jika gagal menyimpan file (`AccessDeniedException`), cek owner folder:

```bash
sudo chown -R 10001:10001 storage
sudo chmod 700 storage
```

Jika URL respons masih `localhost`, edit `APP_PUBLIC_BASE_URL` di `.env` dan jalankan `docker compose up -d --force-recreate`.

## 8. Cek data manifest di dock_ship

**GET** `/checkmanifets` (alias `/checkmanifest`), memakai Basic Auth dengan authority `MANIFEST_CHECK`.

| Parameter | Kolom `dock_ship` |
| --- | --- |
| `id` | `id` |
| `branch_id` | `branch_id` |
| `harbour_id` | `harbor_id` |
| `route_id` | `route_id` |

Semua parameter wajib. Baris dianggap ditemukan hanya jika keempat nilainya cocok.

```bash
curl -sS -u "$API_USER:$API_PASSWORD" \
  "http://127.0.0.1:8080/checkmanifets?id=ID_DOCK_SHIP&branch_id=1011&harbour_id=348&route_id=5510"
```

Response HTTP **200** berisi `{"found": true, "data": { ...kolom dock_ship... }}`, atau `{"found": false, "data": null}` jika tidak ada. Parameter kosong atau tidak valid: HTTP **400**. Kredensial salah: HTTP **401**. Tanpa authority: HTTP **403**. Database tidak dapat diakses: HTTP **503**.

### Manifest not approve

**POST** `/manitfestnotapprove?id={id}&value={0|1}` (alias `/manifestnotapprove`), memakai Basic Auth dengan authority `MANIFEST_APPROVAL_RESET`. Mengisi `dock_ship.tootlip` dengan `value` untuk baris dengan `id` tersebut (nama kolom di database memang `tootlip`); `approve_manifest` tidak diubah. `value` hanya boleh `0` atau `1`, dan bila tidak dikirim dianggap `1`; kolom lain tidak diubah.

```bash
curl -sS -X POST -u "$API_USER:$API_PASSWORD" \
  "http://127.0.0.1:8080/manitfestnotapprove?id=ID_DOCK_SHIP&value=1"
```

Response HTTP **200** `{"id": "...", "updated": true, "tootlip": <value>}`, termasuk jika nilainya sudah sama. `id` tidak ada: HTTP **404** `{"updated": false, "tootlip": null}`. `id` kosong, atau `value` selain `0`/`1`: HTTP **400**. Database tidak dapat diakses: HTTP **503**.

### Database

Koneksi database diatur lewat `MANIFEST_DB_URL`, `MANIFEST_DB_USERNAME`, dan `MANIFEST_DB_PASSWORD` di `.env`. Ketiganya wajib diisi.

Query memakai Spring Data JPA (Hibernate), tanpa SQL manual: entity `DockShip` dan `DockShipRepository`. Repository hanya punya method cek (transaksi read-only) dan update `tootlip`; tidak ada method simpan atau hapus, dan `ddl-auto=none` sehingga struktur tabel tidak pernah diubah.

## 9. Autentikasi (HTTP Basic, Spring Security)

**Basic Auth wajib dipakai lewat HTTPS/TLS.** Header `Authorization: Basic ...` hanya Base64 dari `username:password`, bukan enkripsi. Siapa pun yang menyadap koneksi HTTP biasa bisa membaca password-nya.

Spring Security membaca dan memvalidasi header `Authorization` pada setiap request terproteksi. Autentikasi bersifat stateless: tidak ada session maupun cookie. Password diverifikasi dengan BCrypt (`PasswordEncoder`), dan aplikasi hanya menyimpan hash-nya.

```bash
curl -i \
  -u username:password \
  "https://pdf.example.com/checkmanifets?id=ID_DOCK_SHIP&branch_id=1011&harbour_id=348&route_id=5510"
```

### Endpoint publik dan terproteksi

| Endpoint | Akses |
| --- | --- |
| `GET /api/v1/manifests/files/{id}`, `POST /api/v1/manifests/files/{id}/unlock` | Publik (dilindungi password cutOffId) |
| `POST /api/v1/manifests/generate-pdf` | Basic Auth + authority `MANIFEST_GENERATE` |
| `GET /checkmanifets`, `GET /checkmanifest` | Basic Auth + authority `MANIFEST_CHECK` |
| `POST /manitfestnotapprove`, `POST /manifestnotapprove` | Basic Auth + authority `MANIFEST_APPROVAL_RESET` |
| Path lain | Basic Auth (user terautentikasi apa pun) |

### Respons error

```json
{
  "code": "AUTH-401",
  "message": "Kredensial tidak valid",
  "timestamp": "2026-11-10T11:38:23.161Z",
  "path": "/api/v1/manifests/generate-pdf",
  "correlationId": "e2e-001"
}
```

| HTTP | `code` | Arti |
| --- | --- | --- |
| 401 | `AUTH-401` | Header tidak ada, username tidak dikenal, atau password salah. Pesannya sama untuk semua kasus agar username tidak bisa ditebak |
| 401 | `AUTH-USER-DISABLED` | Password benar tetapi akun dinonaktifkan |
| 403 | `AUTH-403` | Login berhasil tetapi user tidak punya authority untuk endpoint tersebut |
| 500 | `AUTH-INTERNAL-ERROR` | Kegagalan internal saat autentikasi. Detailnya hanya ada di log server |

Kirim header `X-Correlation-Id` untuk melacak request. Nilainya hanya boleh berisi huruf, angka, `.`, `_`, atau `-`, maksimal 64 karakter. Jika header tidak dikirim, server membuat UUID. Nilai ini dikembalikan di header respons, di field `correlationId`, dan muncul di setiap baris log.

### Konfigurasi

Konfigurasi ada di `src/main/resources/application.properties` dengan prefix `asdp.security.basic-auth`. Nilai sensitif diambil dari environment variable, yaitu `.env` untuk Docker Compose:

| Environment variable | Wajib | Keterangan |
| --- | --- | --- |
| `MANIFEST_API_USERNAME` | Ya | Username client, tanpa `:` dan spasi |
| `MANIFEST_API_PASSWORD_HASH` | Ya | Hash BCrypt dari password. Password asli ditolak saat aplikasi start |
| `MANIFEST_API_AUTHORITIES` | Ya | Daftar authority dipisah koma. Berikan hanya yang dibutuhkan |
| `MANIFEST_API_ENABLED` | Tidak (default `true`) | `false` menonaktifkan akun |
| `ASDP_BASIC_AUTH_REALM` | Tidak (default `asdp-internal-api`) | Realm pada header `WWW-Authenticate` |
| `ASDP_BASIC_AUTH_ENABLED` | Tidak (default `true`) | `false` mematikan Basic Auth dan **membuka semua endpoint**. Hanya untuk pengembangan lokal |

Endpoint publik diatur lewat `asdp.security.basic-auth.public-paths`. User tambahan bisa didaftarkan sebagai `asdp.security.basic-auth.users[1].*`, atau lewat environment variable `ASDP_SECURITY_BASICAUTH_USERS_1_USERNAME`, `ASDP_SECURITY_BASICAUTH_USERS_1_PASSWORDHASH`, dan `ASDP_SECURITY_BASICAUTH_USERS_1_AUTHORITIES`.

### Cara membuat hash BCrypt

Cara berikut tidak menyimpan password di riwayat shell. Contoh dengan Docker:

```bash
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 ""
```

Masukkan password saat diminta, lalu ambil bagian setelah `:` (diawali `$2y$12$`). Simpan di `.env` **dengan tanda kutip tunggal** agar Docker Compose tidak memproses karakter `$`:

```dotenv
MANIFEST_API_PASSWORD_HASH='$2y$12$...'
```

## 10. Testing

```bash
# Bila JDK 21 (misalnya Oracle JDK 21) & Maven 3.9.6 tersedia tanpa Docker
mvn clean test
export MANIFEST_API_USERNAME=ganti-username MANIFEST_API_PASSWORD_HASH='$2y$12$...' MANIFEST_API_AUTHORITIES=MANIFEST_GENERATE,MANIFEST_CHECK
mvn spring-boot:run
```

Unit test mencakup pembuatan PDF, QR payload, random filename, pembacaan storage, validasi dan password cutOffId, penguncian setelah lima percobaan salah, dan Basic Auth (unit test dengan Mockito serta integration test lewat server embedded: 401, 403, akun nonaktif, endpoint publik, mode nonaktif, dan tidak ada credential di log maupun respons).
