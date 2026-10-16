---
title: Công nghệ đằng sau Soundscape
layout: page
parent: "Sử dụng Soundscape"
has_toc: false
nav_order: 200
lang: vi
permalink: /users/how-it-works.html
machine-translated: true
translated-from: 1d7f81a05233463dbc2eb8bf62ea26979f5114d0
---

# Công nghệ đằng sau Soundscape
Mục đích của trang này là giúp bạn hiểu khái quát cách ứng dụng Soundscape hoạt động bên trong. Bạn không cần đọc trang này để dùng ứng dụng, nhưng trang được viết vì một vài lý do:

1. Giúp những người mới quan tâm hiểu được các giới hạn của ứng dụng
1. Cho người dùng biết những gì khác có thể làm được với các tính năng mới
1. Cho nhà phát triển cái nhìn tổng quan về cách ứng dụng hoạt động

Có hai công nghệ giúp ứng dụng hoạt động: GPS và dữ liệu OpenStreetMap. GPS cho chúng tôi biết khá rõ điện thoại đang ở đâu và đã ở đâu. Sau đó, dữ liệu OpenStreetMap được dùng để tìm những gì ở gần đó và mô tả chúng cho người dùng.

## Đèn hiệu âm thanh
Xét về công nghệ, đây là phần đơn giản nhất để thực hiện. Nếu biết vị trí của điện thoại và hướng điện thoại đang chỉ, chúng tôi có thể thay đổi âm thanh của đèn hiệu sao cho nghe như nó phát ra từ hướng đó. Chúng tôi dùng một thư viện của Steam Audio để định vị âm thanh, thư viện này dùng các hàm truyền liên quan đến đầu (HRTF) để vị trí âm thanh nghe rõ nhất có thể. Việc duy nhất khác mà chúng tôi làm là thay đổi âm thanh đèn hiệu để phát một âm khác tùy theo góc giữa hướng của người dùng và vị trí của đèn hiệu. Các góc này khác nhau tùy theo kiểu đèn hiệu được chọn (*Gõ nhịp*, *Tia sáng*, *Ping*, v.v.) và một số kiểu có nhiều âm hơn các kiểu khác. Đó là Đèn hiệu âm thanh ở mức đơn giản nhất.

Phức tạp thêm duy nhất là các giả định về vị trí của điện thoại và hướng mà người dùng đang chỉ. Hãy lần lượt xem xét từng điều.

### Vị trí
Vị trí do GPS trả về có thể có sai số khá lớn. Sai số này phụ thuộc vào việc GPS của điện thoại nhìn thấy được bao nhiêu phần bầu trời, và có bao nhiêu cây cối và tòa nhà cao tầng phản xạ tín hiệu GPS trên đường đến điện thoại.

Để lọc vị trí, chúng tôi dùng một kỹ thuật gọi là khớp bản đồ (map matching). Kỹ thuật này giả định rằng người dùng nhiều khả năng đang di chuyển dọc theo một lối đi hoặc con đường có trên bản đồ; chúng tôi dùng thuật ngữ "tuyến đường" (tiếng Anh là "Way") để chỉ mọi con đường, đường mòn và lối đi. Khớp bản đồ xem xét người dùng đã đi qua đâu, rồi dựa vào hướng di chuyển cùng dữ liệu bản đồ khu vực để chọn vị trí có khả năng nhất trên một tuyến đường. Cách này không chỉ bù cho sai số của GPS mà còn cho cả sai số trong dữ liệu bản đồ. Không phải tuyến đường nào cũng được vẽ chính xác, nên chúng cũng có sai số. Để xác định tuyến đường mà người dùng nhiều khả năng đang ở trên, thuật toán xem xét:
* Tuyến đường gần vị trí GPS hiện tại và các vị trí GPS trước đó đến mức nào
* Hướng di chuyển: người dùng có đang đi cùng hướng với tuyến đường không
* Có thể đi từ vị trí khớp bản đồ gần nhất đến vị trí mới qua mạng lưới tuyến đường hay không. Điều này cần để loại trừ việc nhảy giữa các tuyến đường không thực sự nối với nhau, ví dụ một tuyến đi qua trên tuyến kia bằng cầu, hoặc đi bên dưới qua đường hầm.
Khớp bản đồ có thể kết luận rằng không có tuyến đường nào ở gần, hoặc chưa chắc người dùng đang ở tuyến nào. Khi đó, nó chỉ chờ vị trí GPS tiếp theo và thử lại cho đến khi chắc chắn.

### Hướng
Phần mềm theo dõi một vài loại hướng:

1. Hướng mà điện thoại đang chỉ. Chúng tôi dùng hướng này khi điện thoại mở khóa và ứng dụng đang được dùng, nhưng cũng dùng khi điện thoại khóa, miễn là điện thoại được giữ nằm ngang với màn hình hướng lên trời. Bạn nên lưu ý điều này khi cho điện thoại vào túi. Nếu đặt nằm ngang dưới đáy một chiếc túi được đeo thẳng đứng, ứng dụng sẽ dùng hướng ngẫu nhiên mà chiếc túi đang chỉ.
1. Hướng mà điện thoại đang di chuyển.
1. Hướng từ tai nghe có theo dõi chuyển động đầu. Hiện chúng tôi chưa dùng hướng này, dù ứng dụng iOS từng hỗ trợ. Chúng tôi đã có sẵn công nghệ để thêm vào trong tương lai.

Khi điện thoại khóa và ở trong túi, ứng dụng sẽ dùng hướng di chuyển. Tuy nhiên, nếu người dùng không di chuyển thì không có hướng nào. Khi đó, Đèn hiệu âm thanh sẽ nhỏ đi để cho biết rằng không thể biết hướng hiện tại của đèn hiệu: người dùng có thể đang quay người mà không đổi vị trí.

Với một số cách dùng hướng trong ứng dụng, hướng sẽ được "bám" theo hướng của tuyến đường đã khớp bản đồ. Vì vậy, nếu người dùng đi gần như theo hướng của tuyến đường, hướng thực tế của tuyến đường được coi là đúng và được dùng trong các phép tính đó.

### Kết luận
Dù thoạt nhìn Đèn hiệu âm thanh có vẻ đơn giản, việc dùng khớp bản đồ để cố loại bỏ sai số về vị trí và hướng làm tăng đáng kể độ phức tạp.

## Dữ liệu bản đồ
Dữ liệu bản đồ mà ứng dụng dùng hầu hết đều đến từ dự án OpenStreetMap. Chúng tôi vận hành một máy chủ chứa bản đồ toàn thế giới ở nhiều mức thu phóng. Mỗi mức thu phóng được chia thành các ô. Mức 0 có 1 ô, mức 1 có 4 ô, mức 2 có 16 ô và cứ thế cho đến mức 14, có khoảng 268 triệu ô để phủ kín hành tinh. Mỗi ô có nhiều lớp và mỗi lớp có các điểm, đường và đa giác có thể vẽ thành bản đồ đồ họa. Bản đồ đồ họa đó là những gì hiển thị cho người dùng trên màn hình ứng dụng. Mỗi điểm, đường và đa giác có siêu dữ liệu mô tả nó là gì. Phần lớn lấy thẳng từ dữ liệu OpenStreetMap, nên một đường có thể là `footway` thuộc loại `sidewalk`, hoặc `road` thuộc loại `minor`.

Dữ liệu được chuyển thành bản đồ đồ họa thông qua một "kiểu", có các quy tắc vẽ các điểm, đường và đa giác khác nhau trong mỗi lớp, ví dụ cách vẽ một lối đi, một khu rừng hay một trạm xe buýt. Quy tắc có thể khác nhau theo mức thu phóng, vì vậy khi bạn phóng to, ngày càng nhiều điểm và đường xuất hiện mà khi thu nhỏ không thấy, ví dụ trạm xe buýt và lối đi.

Bằng cách thay đổi kiểu, chúng tôi có thể thay đổi giao diện bản đồ trên màn hình, và "bản đồ dễ tiếp cận" mà chúng tôi đang thử nghiệm ra đời từ đó. Bản đồ này hướng đến độ tương phản cao hơn, đường nét và chữ đậm hơn. Kiểu được tích hợp sẵn trong ứng dụng, nên chúng tôi không phải thay đổi bản đồ trên máy chủ để đổi giao diện.

Nhưng chúng tôi dùng dữ liệu bản đồ cho âm thanh như thế nào?

### Dùng dữ liệu bản đồ cho âm thanh
Hiện chúng tôi chỉ dùng một phần tương đối nhỏ dữ liệu bản đồ để tạo giao diện âm thanh. Gần như toàn bộ giao diện âm thanh chỉ dùng các ô ở mức thu phóng cao nhất. Ứng dụng ghép một lưới 2 x 2 ô quanh vị trí người dùng, rồi chỉ xem xét một vài lớp:

* `transportation` - cho mọi loại tuyến đường, gồm đường xe chạy, lối đi, đường sắt và đường xe điện.
* `poi` - các địa điểm quan tâm, ví dụ cửa hàng, trung tâm thể thao, ghế dài, hộp thư, trạm xe buýt, v.v.
* `building` - cho các `poi` được vẽ không chỉ là một điểm, ví dụ siêu thị lớn hoặc tòa thị chính.

Ứng dụng nối các đường và đa giác qua ranh giới giữa các ô và biến mọi tuyến đường thành các đoạn tuyến và giao lộ liên kết với nhau. Điều này quan trọng vì nó cho phép chúng tôi tìm dọc theo một tuyến đường để biết có thể đến được đâu.

Toàn bộ dữ liệu đã xử lý cũng được lưu ở định dạng dễ tìm kiếm để ứng dụng có thể dễ dàng biết những đối tượng nào trên bản đồ đang ở gần. Ở bước này, dữ liệu được phân loại. Các loại hiện có là:

* Đường xe chạy
* Đường xe chạy và lối đi (mọi tuyến đường)
* Giao lộ: các điểm mà tuyến đường cắt nhau
* Lối vào: các điểm trên tòa nhà được đánh dấu là lối vào.
* Chỗ sang đường: nơi băng qua đường
* Địa điểm quan tâm: mọi địa điểm quan tâm
* Trạm giao thông công cộng: trạm xe buýt, ga tàu, trạm xe điện, v.v.
* Các loại con của địa điểm quan tâm:
  * Thông tin
  * Đồ vật
  * Địa điểm
  * Điểm mốc
  * Di chuyển
  * An toàn
* Khu dân cư và các loại con (xem phần tiếp theo)
  * Thành phố
  * Thị trấn
  * Làng
  * Xóm

Với những điều này, ở bất kỳ vị trí nào ứng dụng cũng có thể dễ dàng tìm

* "Tất cả trạm giao thông công cộng trong vòng 50 m" hoặc
 * "Giao lộ gần nhất phía trước tôi" hoặc
 * "Xóm, làng, thị trấn hoặc thành phố gần nhất"

Với những điều này, tạo thông báo chỉ là việc truy vấn dữ liệu theo vị trí và hướng hiện tại. Khi người dùng di chuyển qua lưới ô, ứng dụng cập nhật lưới để nó luôn có tâm quanh vị trí hiện tại.

### Thêm dữ liệu
Một vấn đề của lưới dữ liệu bản đồ rất cục bộ của chúng tôi là chúng tôi chỉ có thể "nhìn thấy" tối đa khoảng 1 km theo mọi hướng. Như vậy là đủ để mô tả những gì phía trước, nhưng đôi khi chúng tôi muốn cung cấp thêm bối cảnh. Ví dụ chính là khi ứng dụng được dùng mà người dùng không đi bộ.

Khi ứng dụng phát hiện người dùng di chuyển nhanh hơn 5 mét mỗi giây, nó thay đổi cách mô tả thế giới. Thay vì thông báo mọi giao lộ và địa điểm quan tâm, ứng dụng thông báo thưa hơn và chỉ về những con đường gần đó. Vấn đề là biết tên một con đường cũng không giúp được nhiều nếu bạn không biết nó ở thị trấn nào.

Để giải quyết, giờ đây chúng tôi còn xử lý dữ liệu bản đồ ở mức thu phóng thấp hơn và lấy dữ liệu từ lớp `place`. Lớp này chứa tên của thị trấn, thành phố, khu phố, làng, v.v. Một khó khăn khi lập bản đồ là không phải lúc nào cũng có ranh giới rõ ràng giữa các nơi này. Đôi khi OpenStreetMap có ranh giới thành phố trong cơ sở dữ liệu, nhưng ngay cả khi đó, thông tin này thường bị mất khi đến bản đồ dạng ô của chúng tôi. Thứ chúng tôi có là vị trí nơi tên địa danh được vẽ trên bản đồ. Các tên này được phân loại, rồi ứng dụng sẽ tìm xóm, làng, thị trấn hoặc thành phố gần người dùng nhất và thông báo.

Với nhiều thành phố, tên thành phố sẽ không bao giờ được thông báo, vì phần lớn thành phố được chia thành các khu nhỏ hơn như khu phố, nhưng các khu này cho thêm bối cảnh và rất hữu ích. Chỉ cần nhớ rằng khi ứng dụng báo bạn đang ở gần một con phố trong một khu phố nào đó, điều đó chỉ có nghĩa nhãn của khu phố đó là điểm gần nhất, và nó có thể sai hoặc thậm chí ở bên kia sông.

### Thêm bối cảnh
Càng thêm được nhiều bối cảnh vào mô tả càng tốt, miễn là vẫn ngắn gọn và dễ đoán. Một trong những vấn đề chúng tôi gặp khi mô tả giao lộ là thường có các tuyến đường "không tên". Đây là những tuyến đường không có tên. Trong dữ liệu bản đồ, chúng có thể chỉ là đường đất, lối đi hoặc đường nội bộ, nhưng nếu không có thêm bối cảnh thì chúng không hữu ích lắm trong mô tả. May mắn là chúng tôi có thể làm tốt hơn: mỗi khi sắp thông báo một tuyến đường không tên, ứng dụng xem có thể tìm thêm bối cảnh cho nó hay không.

* **Đó có phải vỉa hè không?**
Nhiều khu vực trên OpenStreetMap hiện vẽ vỉa hè riêng với đường xe chạy. Chúng thường được gắn thẻ `sidewalk` nhưng thường không cho biết đó là vỉa hè của con đường nào.

Khi gặp một vỉa hè không tên, ứng dụng tìm một con đường mà nó cho là chạy song song bên cạnh và dùng tên con đường đó để đặt tên cho vỉa hè. Điều này hóa ra rất quan trọng với các thông báo của chúng tôi. Thay vì thông báo mọi giao lộ của vỉa hè, khi chúng tôi đi dọc vỉa hè, thông báo được đưa ra như thể chúng tôi đang đi dọc con đường tương ứng. Thay vì *"Đang đi về hướng tây trên lối đi"* chúng tôi nghe *"Đang đi về hướng tây trên Moor Road"*. Người dùng đang ở trên vỉa hè đã được vẽ, nhưng mô tả hợp lý hơn.

* **Nó có kết thúc ở một tuyến đường có tên không?**
Rất thường có những lối đi bộ nối hai con đường với nhau. Bằng cách xem cả hai đầu lối đi, chúng tôi dễ dàng thêm được bối cảnh đó, nên theo một hướng có thể là *"Lối đi đến Moor Road"* và khi đến từ đầu kia có thể là *"Lối đi đến Roselea Drive"*. Việc này chỉ được làm khi lối đi không rẽ nhánh; nếu nó tách thành hai lối đi không tên thì chúng tôi không cố thêm bối cảnh này.

* **Nó có kết thúc gần một Điểm đánh dấu không?**
Nếu một tuyến đường không tên bắt đầu hoặc kết thúc gần một Điểm đánh dấu, Điểm đánh dấu đó được dùng để mô tả nó, ví dụ *"Lối đi đến ngã ba cây lớn"*. Người dùng có thể thêm Điểm đánh dấu ở bất cứ đâu, và bằng cách thêm Điểm đánh dấu dọc theo mạng lưới lối đi, họ có thể thêm bối cảnh cho cả một lộ trình.

* **Nó có đi vào hoặc ra khỏi một địa điểm quan tâm không?**
Nếu một tuyến đường không tên bắt đầu bên ngoài một địa điểm quan tâm và kết thúc bên trong (hoặc ngược lại), chúng tôi có thể thêm bối cảnh đó, ví dụ *"Đường đất đến Lennox Park"*.

* **Nó có kết thúc gần một lối vào không?**
Nếu một tuyến đường không tên bắt đầu hoặc kết thúc gần một lối vào hơn, chúng tôi có thể thêm bối cảnh đó, ví dụ *"Đường nội bộ đến Best Buy"*.

* **Nó có kết thúc gần một Điểm mốc hoặc địa điểm không?**
Nếu một tuyến đường không tên bắt đầu hoặc kết thúc gần một Điểm mốc hơn, chúng tôi cũng có thể thêm bối cảnh đó, ví dụ *"Đường nội bộ đến Nhà thờ St Giles"*.

* **Đó có phải đường cụt không?**
Ứng dụng đánh dấu là đường cụt mọi tuyến đường không tên không dẫn đến đâu.

* **Nó có đi qua cầu, đường hầm hoặc cầu thang không?**
Nếu tuyến đường không tên đi qua cầu, qua đường hầm hoặc lên/xuống cầu thang, điều này được ghi nhận và thêm vào bối cảnh. Việc này tách biệt với việc ghi điểm đến, nên có thể có bối cảnh như *"Lối đi qua cầu đến Lennox Park"*.

Các bối cảnh này được thêm theo thứ tự, nên có thể có *"Lối đi đến Park Lane qua cầu thang"* theo một hướng và *"Lối đi đến Lennox Park qua cầu thang"* theo hướng kia. Con phố có tên được ưu tiên khi rời công viên, còn công viên được dùng khi đi vào.

#### Bối cảnh trong tương lai
Có nhiều bối cảnh khác chúng tôi hy vọng sẽ thêm trong tương lai, bao gồm:
* Bối cảnh cho tuyến đường men theo dòng nước, ví dụ *"Lối đi cạnh sông Dee"*
* Bối cảnh cho tuyến đường men theo bờ hồ hoặc hồ chứa, ví dụ *"Lối đi cạnh hồ chứa Milngavie"*
* Bối cảnh cho tuyến đường men theo đường sắt, ví dụ *"Lối đi cạnh đường sắt"*. Thậm chí có thể có cả tên tuyến đường sắt
* Thêm nội dung cho cầu và đường hầm, chúng đi qua trên hoặc dưới cái gì, ví dụ *"Lối đi qua cầu vượt đường sắt đến Moor Road"*

## Thông báo âm thanh
Giờ khi đã có dữ liệu bản đồ ở định dạng dễ dùng, việc tạo thông báo thật sự khá đơn giản.

### Thông báo khi đi bộ
Khi đi bộ, các thông báo có thể phát ra là (theo thứ tự ưu tiên):

1. Cho biết điểm đến hiện tại còn cách bao xa
1. Mô tả giao lộ sắp tới
1. Mô tả 5 địa điểm quan tâm gần nhất

Mọi thông báo đều bị giới hạn tần suất để không lặp lại quá thường xuyên. Nếu người dùng ngừng di chuyển, thông báo sẽ dừng, và ngay cả khi đang di chuyển, một thông báo cũng không lặp lại với mỗi vị trí GPS mới. Tần suất, giống ứng dụng iOS, là:

* Mỗi 60 giây cho điểm đến hiện tại
* Mỗi 30 giây cho giao lộ sắp tới
* Mỗi 60 giây cho một địa điểm quan tâm

Có thể lọc thông báo trong menu *Cài đặt*, và chắc chắn còn chỗ để mở rộng cách hoạt động này.

### Thông báo khi di chuyển nhanh hơn
Khi di chuyển nhanh hơn 5 mét mỗi giây, thông báo về điểm đến hiện tại vẫn được phát, nhưng thông báo về giao lộ và địa điểm quan tâm được thay bằng một thông báo mô tả đại khái người dùng đang ở đâu. Thông báo này nêu một trạm giao thông công cộng gần đó, một địa điểm quan tâm mà chúng tôi đang ở bên trong (ví dụ trong một công viên lớn), hoặc một con đường và khu dân cư gần đó. Các thông báo này dùng dữ liệu đã mô tả ở trên, và rõ ràng có thể cho phép tùy chỉnh trong tương lai.

## Điểm đánh dấu và Lộ trình
Phần lớn, Điểm đánh dấu và Lộ trình chỉ là tính năng giao diện người dùng, không phụ thuộc vào GPS lẫn dữ liệu bản đồ. Điểm đánh dấu là các vị trí có tên mà người dùng muốn lưu, còn Lộ trình là danh sách có thứ tự của các Điểm đánh dấu đó. Giao diện để tạo cả hai được lấy trực tiếp từ phiên bản iOS.

### Phát lộ trình
Khi được phát, lộ trình trở nên sống động. Khi một lộ trình được phát, một Đèn hiệu âm thanh được tạo tại Điểm đánh dấu đầu tiên của lộ trình. Khi người dùng đến gần Điểm đánh dấu đó, lộ trình tự động chuyển Đèn hiệu âm thanh sang Điểm đánh dấu tiếp theo. Nếu không còn Điểm đánh dấu nào, việc phát lộ trình kết thúc.

## Kết luận
Hy vọng trang này đã giúp bạn hiểu thêm về cách ứng dụng hoạt động. Ứng dụng luôn được phát triển dựa trên phản hồi của người dùng, vì vậy hãy liên hệ với chúng tôi nếu bạn nghĩ có điều gì đó nên được thêm vào.
