# Manifest PDF Service — Java 25 + Spring Boot 4.1.1

Menerima JSON manifest kendaraan/penumpang, membuat PDF A4 portrait berisi daftar penumpang pada kendaraan, daftar penumpang pejalan kaki, daftar barang curah dan hewan, serta rekapitulasi, dan membubuhkan QR JSON berisi `id_boarding`, `approved_by_operator`, `tanggal_approve_manifest`, `id_pkk` ketika `id_pkk` tersedia, lalu **menyimpan PDF ke storage lokal** dan mengembalikan **URL acak**. Saat URL dibuka, browser menampilkan formulir password **DDMMYYYY** dari **tanggal saat URL diakses (Asia/Jakarta/WIB)**. Setelah password benar, PDF muncul di browser.

**PENTING:** Password tanggal sangat mudah ditebak. Random URL menyulitkan penemuan dokumen tetapi tidak menggantikan autentikasi. Untuk manifest yang memuat identitas pribadi, batasi akses melalui HTTPS, autentikasi API, firewall, logging akses, kebijakan retensi file, dan bila mungkin gunakan password independen yang kuat. Contoh ini tidak mengenkripsi PDF di storage dan tidak menggunakan PDF document password; proteksi berada pada endpoint HTML sebelum file diberikan.

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
│   ├── qr-content.json
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
MANIFEST_API_PASSWORD=ganti-password
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70.0 -Duser.timezone=Asia/Jakarta -Dfile.encoding=UTF-8
```

`MANIFEST_API_USERNAME` dan `MANIFEST_API_PASSWORD` wajib diisi; tanpa keduanya aplikasi menolak start. Jangan commit nilainya ke Git (`.env` sudah di-ignore).

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

Endpoint ini memakai HTTP Basic Auth dengan kredensial dari `MANIFEST_API_USERNAME` dan `MANIFEST_API_PASSWORD`. Kredensial kosong atau salah: HTTP **401** dengan header `WWW-Authenticate`. Basic Auth hanya meng-encode kredensial (bukan enkripsi), jadi gunakan HTTPS di luar localhost. Endpoint `/files/...` tidak memakai Basic Auth karena dilindungi password tanggal.

Body (file lengkap: `examples/request.json`):

```json
{
  "document": {
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

- `vehicles`, `pedestrians`, dan `cargo` opsional, tetapi minimal salah satunya harus berisi data. Daftar yang kosong tetap ditampilkan sebagai tabel berisi header saja.
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
  "passwordFormat": "DDMMYYYY",
  "passwordTimezone": "Asia/Jakarta"
}
```

Perintah cURL:

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/manifests/generate-pdf \
  -u "$MANIFEST_API_USERNAME:$MANIFEST_API_PASSWORD" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  --data-binary @examples/request.json
```

Atau jalankan `bash examples/call-api.sh` (Linux/macOS), `./examples/call-api.ps1` (PowerShell). Kedua skrip membaca `MANIFEST_API_USERNAME` dan `MANIFEST_API_PASSWORD` dari environment.

## 4. Buka URL dengan password

- **GET** `/api/v1/manifests/files/{fileId}`: menampilkan halaman formulir password (HTML).
- **POST** `/api/v1/manifests/files/{fileId}/unlock`: menerima field HTML form `password`; jika benar, memberikan `application/pdf`, dengan `Content-Disposition: inline`.
- Password berasal dari **tanggal saat akses**, mengikuti `MANIFEST_ACCESS_ZONE` (default WIB), bukan tanggal manifest/generate. Contoh 23 Oktober 2026 => **23102026**, 24 Oktober 2026 => **24102026**.
- Salah password: HTTP **401** + form peringatan. Setelah 5 percobaan salah: **429** dan dikunci 15 menit (status kunci disimpan di RAM, bukan persisten).
- Token tidak valid atau file tidak ada: HTTP **404**.
- Browser: salin field `url` dari response dan buka URL; ketik DDMMYYYY; klik **Buka PDF**.

Untuk mengunduh memakai command line:

```bash
# Ganti FILE_ID dari response API
FILE_ID='pNOh-uCNnEluOQC4vFczgWbSUKkfeCKFRPJzz0Q_0vU'
PASSWORD="$(TZ=Asia/Jakarta date +%d%m%Y)"

curl -fsS -X POST "http://127.0.0.1:8080/api/v1/manifests/files/$FILE_ID/unlock" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "password=$PASSWORD" \
  --output manifest-penumpang.pdf
```

**Catatan:** Mengakses GET link dengan cURL hanya menerima HTML password, bukan file PDF. File PDF hanya dikirim setelah POST unlock valid.

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
(`document`), bukan random URL:

```json
{
  "id_boarding": "B22608190999",
  "approved_by_operator": "Dharma Lautan Utama",
  "tanggal_approve_manifest": "2026-08-17T17:17:10",
  "id_pkk": "PKK123213ABC"
}
```

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

## 8. Testing

```bash
# Bila Java 25 & Maven tersedia tanpa Docker
mvn clean test
export MANIFEST_API_USERNAME=ganti-username MANIFEST_API_PASSWORD=ganti-password
mvn spring-boot:run
```

Unit test mencakup pembuatan PDF, QR payload, random filename, pembacaan storage, timezone WIB, penguncian setelah lima percobaan salah, dan pemeriksaan Basic Auth. **Pengujian end-to-end build Docker dan runtime HTTP perlu dilakukan di server**, karena lingkungan penyusunan project belum memiliki Docker/Java 25/Maven.
