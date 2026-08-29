# Архитектура консольного приложения "Sorter"

## Часть 1. Текстовое описание архитектуры

### 1.1. Назначение и общий замысел

Приложение представляет собой консольную утилиту на Java (17+), работающую в бесконечном цикле и предоставляющую пользователю возможность сформировать коллекцию объектов класса **Автобус** (`Bus` с полями «номер маршрута», «модель», «пробег»), отсортировать её собственной реализацией алгоритма **Timsort** по любому из трёх полей в обоих направлениях, а также воспользоваться дополнительными режимами: сортировкой «только чётных» элементов, дозаписью результатов в файл, многопоточным подсчётом вхождений элемента. Выход из цикла возможен исключительно через явный выбор соответствующего пункта меню. Все готовые реализации сортировки, поиска и паттернов не используются — Timsort, компараторы-комбинаторы, Builder, Стратегия и кастомная коллекция реализованы вручную.

Замысел архитектуры — гибрид объектно-ориентированной структуры пакетов и функционального стиля там, где он даёт выгоду: доменные объекты неизменяемы, все точки расширения выражены функциональными интерфейсами, конфигурация поведения собирается композицией функций и лямбда-выражений, а изменяемое состояние приложения сознательно локализовано в одном классе. Это и есть «максимально функциональный стиль в разумных пределах»: чистые функции в ядре (валидация, сортировка, парсинг), лямбды и стримы на границах, но при этом понятная пакетная структура и явные паттерны Builder и Strategy, требуемые заданием.

### 1.2. Слои и пакеты

Система разделена на шесть пакетов, отражающих слои ответственности. Корневой пакет по конвенции Java именуется по домену организации, например `ru.university.bussort`, внутри которого располагаются подпакеты `domain`, `validation`, `collection`, `data`, `algorithm`, `io`, `concurrent` и `ui`. Зависимости направлены строго сверху вниз: слой представления (`ui`) знает обо всех слоях, слой алгоритмов и данных зависит только от домена и валидации, а домен не зависит ни от кого. Инфраструктурный пакет `io` (чтение консоли и запись файлов) и пакет `concurrent` используются только слоем представления. Такая направленность исключает циклические зависимости и позволяет тестировать ядро (валидацию, Timsort, парсер) без консоли и файловой системы.

### 1.3. Доменная модель и паттерн Builder

Класс `Bus` спроектирован как неизменяемый value-объект: он объявлен `final`, все три поля (`routeNumber: int`, `model: String`, `mileage: long`) помечены `private final`, доступ осуществляется методами-аксессорами без префикса `get` (в стиле `record`-подобных проекций), сеттеры отсутствуют в принципе, а `equals`, `hashCode` и `toString` реализованы по контракту — `equals` понадобится многопоточному подсчёту вхождений. Создание объекта возможно только через вложенный статический класс `BusBuilder`, реализующий паттерн Builder с fluent-интерфейсом: каждый сеттер возвращает сам билдер, а метод `build()` сначала прогоняет накопленное состояние через скомпонованный валидатор и лишь затем конструирует `Bus`. Таким образом, в системе физически не может существовать невалидного экземпляра `Bus` — инварианты гарантируются конструкцией, что соответствует функциональному принципу «делать некорректные состояния невыразимыми».

### 1.4. Функциональная валидация

Валидация построена как композиция чистых функций. Функциональный интерфейс `Validator<T>` имеет единственный метод `validate(T value): ValidationResult` и метод-комбинатор `and`, позволяющий собирать цепочки правил. `ValidationResult` — неизменяемый агрегат ошибок с операцией `combine`, что даёт классическую аппликативную валидацию: пользователь видит сразу все ошибки, а не первую попавшуюся. Фабрика `BusValidators` поставляет правила для каждого поля и композитный валидатор билдера. Для полей установлены следующие ограничения:

- номер маршрута — целое число в диапазоне от 1 до 999;
- модель — непустая строка длиной до 40 символов, допускающая буквы, цифры, пробелы, дефисы и точки;
- пробег — неотрицательное целое число, не превышающее 5 000 000 км.

Если `build()` обнаруживает нарушения, выбрасывается исключение `InvalidDataException`, несущее полный список сообщений; это единственная «нечистая» точка валидации, отделяющая функциональное ядро от императивной обвязки.

### 1.5. Заполнение коллекции: провайдеры, стримы, кастомная коллекция

Заполнение реализовано как семейство поставщиков данных за функциональным интерфейсом `DataProvider<T>` с методом `provide(int size)`. Перечисление `FillMode` связывает пункт меню с конкретным поставщиком, возвращая его из метода `provider()` — по сути это фабрика, записанная в функциональном стиле. `RandomBusProvider` генерирует коллекцию целиком стримом: `Stream.generate(randomBusFactory).limit(size).collect(CustomCollectors.toCustomList())`, где фабрика — `Supplier<Bus>`, выдающий только валидные значения. `ManualBusProvider` в цикле по количеству элементов читает строки формата «номер;модель;пробег», прогоняет их через `BusLineParser` и в случае ошибок повторяет ввод, показывая пользователю все сообщения валидатора. `FileBusProvider` читает файл стримом `Files.lines`, отображает каждую строку парсером в `Parsed<Bus>` (функциональный аналог `Either`), валидные записи собирает в коллекцию, а невалидные пропускает с предупреждением в консоль; если валидных записей нет, выбрасывается исключение. Длина, указываемая пользователем, применяется к случайному и ручному режимам, а в файловом режиме определяется фактическим числом валидных строк.

Ключевой момент дополнительного задания 3*: коллекция не является `ArrayList`. Пакет `collection` содержит `CustomArrayList<T>` — собственную реализацию интерфейса `List<T>` на динамически растущем массиве с самописным итератором (без наследования `AbstractList`), а также утилиту `CustomCollectors.toCustomList()`, возвращающую `Collector`, собранный из ссылок на методы `CustomArrayList::new` и `add`. Именно в эту коллекцию собираются все стримы приложения, включая внутреннюю работу сортировщика.

### 1.6. Timsort и паттерн Стратегия

Ядро пакета `algorithm` — класс `TimSort` с единственной публичной чистой функцией `sort(List<T> source, Comparator<T> order): List<T>`: она копирует вход в рабочий массив, не мутирует оригинал и возвращает новый `CustomArrayList`. Реализация воспроизводит канонический алгоритм: вычисление `minRun` (значение из диапазона 32–64, приводящее длину к степени двойки с точностью до остатка), выделение естественных возрастающих серий с разворотом строго убывающих, достройку коротких серий бинарной сортировкой вставкой, стек серий с проверкой инвариантов `runLen[i-3] > runLen[i-2] + runLen[i-1]` и `runLen[i-2] > runLen[i-1]`, а также слияние `mergeLo`/`mergeHi` с режимом галопирования (`gallopLeft`, `gallopRight`) и временным буфером меньшей из серий. Стабильность гарантируется выбором стороны слияния.

Паттерн Стратегия выражен функционально: `SortStrategy<T>` — интерфейс с методом `sort(List<T>)`, а его реализации не образуют иерархии классов, а собираются фабрично. Полная сортировка по полю — это лямбда, частично применённая к `TimSort::sort` с нужным компаратором; компараторы, в свою очередь, строятся утилитой `ComparatorBuilder` (собственные комбинаторы `comparing`, `thenComparing`, `reversed`), а `BusComparators` даёт три базовых компаратора по всем полям. Перечисления `SortKey` (три поля), `SortDirection` (по возрастанию/убыванию) и `SortMode` (полная сортировка / только чётные) формируют декларативное описание запроса, а класс `SortingService` выступает контекстом стратегии: он единственным выражением собирает из выбранных параметров конкретную функцию сортировки и применяет её к коллекции. Так паттерн Стратегия соблюдён буквально (контекст, интерфейс, семейство взаимозаменяемых алгоритмов), но реализован через композицию функций, а не через наследование.

### 1.7. Режим «только чётные» (дополнительное задание 1)

Отдельная реализация `ParitySortStrategy` решает задачу частичной сортировки по числовому полю «номер маршрута». Алгоритм работает в три чистых шага: сначала через `IntStream` по индексам собираются позиции элементов, у которых ключ чётный; затем соответствующие элементы извлекаются в подсписок с сохранением исходного порядка и сортируются Timsort по натуральному порядку ключа; наконец, отсортированные значения раскладываются обратно по сохранённым индексам. Элементы с нечётным номером маршрута при этом гарантированно остаются на своих исходных позициях. Стратегия переиспользует тот же движок Timsort, что и полные сортировки, что и требуется формулировкой «эти же алгоритмы».

### 1.8. Запись результатов в файл (дополнительное задание 2)

Класс `ResultWriter` инкапсулирует дозапись: метод `appendAll(Path, List<Bus>, Function<Bus, String>)` открывает файл с флагами `CREATE` и `APPEND`, при создании файла пишет строку заголовка, после чего отображает каждый элемент переданным форматтером. Форматтер — это `BusFormatter.toCsv`, передаваемый в `ResultWriter` ссылкой на метод, что сохраняет функциональный стиль: модуль записи ничего не знает о домене, а домен ничего не знает о файлах. Повторные сохранения дополняют файл, а не перезаписывают его.

### 1.9. Многопоточный подсчёт вхождений (дополнительное задание 4)

`OccurrenceCounter` реализует метод `countOccurrences(List<Bus> items, Bus target, int threads)`. Коллекция разбивается на `threads` непрерывных сегментов, для каждого создаётся `Callable<Long>`, подсчитывающий локальное число совпадений по `equals`; задачи отправляются в фиксированный пул через `invokeAll`, после чего частичные суммы сворачиваются редукцией `Long::sum`. Пул создаётся на время вызова и закрывается конструкцией try-with-resources, поэтому операция не оставляет «глобального» изменяемого состояния. Результат выводится в консоль вызывающим кодом.

### 1.10. Консольный интерфейс и главный цикл

Слой `ui` состоит из точки входа `Main`, исполнителя `ApplicationRunner` и меню `ConsoleMenu`. Меню построено декларативно: пункты хранятся в отображении «номер → `MenuAction`», где `MenuAction` — функциональный интерфейс, а сами пункты — лямбды, замыкающиеся на состояние исполнителя. `ApplicationRunner` — единственный владелец изменяемого состояния приложения (исходная коллекция, последний отсортированный результат, флаг `running`); он исполняет цикл `while (running)`, в котором рендерит меню, читает выбор и диспетчеризует действие. Выход из цикла возможен только тогда, когда лямбда пункта «Выход» снимает флаг `running`. Операции сортировки, показа, сохранения и подсчёта защищены guard-условием: при отсутствии заполненной коллекции пользователь получает предупреждение и возвращается в меню. Обёртка `UserInputReader` отвечает за валидированный ввод целых чисел в диапазоне, длин и путей к существующим файлам, конвертируя ошибки формата в понятные сообщения без падения цикла. Ошибки ввода-вывода файлов перехватываются на уровне исполнителя и также не прерывают работу программы.

### 1.11. Организация репозитория и стиль

Репозиторий ведётся на GitHub/GitLab с ветвлением по числу участников; каждая ветка соответствует зоне ответственности и вливается в `main` через merge после ревью. Ожидаемый набор веток:

- `feature/domain-model` — `Bus`, `BusBuilder`, валидация;
- `feature/timsort-engine` — движок Timsort и компараторы;
- `feature/data-providers` — провайдеры, парсер, `CustomArrayList`;
- `feature/console-ui` — меню, цикл, читатель ввода;
- `feature/file-export` — дозапись результатов в файл;
- `feature/concurrency` — многопоточный подсчёт вхождений.

Стиль кода следует конвенциям Java: имена пакетов в нижнем регистре, классы в `UpperCamelCase`, методы и поля в `lowerCamelCase`, константы в `UPPER_SNAKE_CASE`, отступ в четыре пробела, Javadoc на публичных API, объявление неизменяемых ссылок через `final` везде, где это осмысленно.

---

## Часть 2. UML-диаграммы

### 2.1. Обзорная диаграмма слоёв (компонентное представление)

```mermaid
flowchart LR
    subgraph ui ["Слой представления — пакет ui"]
        Main["Main"]
        Runner["ApplicationRunner<br/>(единственное изменяемое состояние)"]
        Menu["ConsoleMenu + MenuAction"]
    end

    subgraph data ["Слой данных — пакеты data, collection"]
        Providers["DataProvider<br/>Random / Manual / File"]
        Parser["BusLineParser + Parsed"]
        CustomList["CustomArrayList + CustomCollectors"]
    end

    subgraph domain ["Доменный слой — пакеты domain, validation"]
        Bus["Bus + BusBuilder (Builder)"]
        Validators["Validator + ValidationResult"]
    end

    subgraph algorithm ["Слой алгоритмов — пакет algorithm"]
        Strategy["SortStrategy (Strategy)<br/>полные и чёт/нечёт"]
        Engine["TimSort (собственная реализация)"]
        Comparators["BusComparators, SortKey,<br/>SortDirection, SortMode"]
    end

    subgraph infra ["Инфраструктура — пакеты io, concurrent"]
        Reader["UserInputReader"]
        Writer["ResultWriter (append)"]
        Counter["OccurrenceCounter (многопоточно)"]
    end

    Main --> Runner
    Runner --> Menu
    Runner --> Reader
    Runner --> Providers
    Runner --> Strategy
    Runner --> Writer
    Runner --> Counter
    Providers --> Parser
    Providers --> CustomList
    Parser --> Bus
    Bus --> Validators
    Strategy --> Engine
    Strategy --> Comparators
    Comparators --> Bus
    Engine --> CustomList
    Writer --> Bus
    Counter --> CustomList
```

### 2.2. Диаграмма классов — домен, валидация, данные, коллекция

```mermaid
classDiagram
    direction TB

    namespace domain {
        class Bus {
            <<immutable>>
            -int routeNumber
            -String model
            -long mileage
            +routeNumber() int
            +model() String
            +mileage() long
            +equals(Object other) boolean
            +hashCode() int
            +toString() String
        }
        class BusBuilder {
            -int routeNumber
            -String model
            -long mileage
            +routeNumber(int value) BusBuilder
            +model(String value) BusBuilder
            +mileage(long value) BusBuilder
            +build() Bus
        }
    }

    namespace validation {
        class Validator~T~ {
            <<functional interface>>
            +validate(T value) ValidationResult
            +and(Validator~T~ next) Validator~T~
        }
        class ValidationResult {
            <<immutable>>
            -List~String~ errors
            +isValid() boolean
            +errors() List~String~
            +combine(ValidationResult other) ValidationResult
            +valid() ValidationResult$
            +invalid(String message) ValidationResult$
        }
        class BusValidators {
            <<utility>>
            +routeNumber() Validator~Integer~$
            +model() Validator~String~$
            +mileage() Validator~Long~$
            +forBuilder() Validator~BusBuilder~$
        }
        class InvalidDataException {
            -List~String~ errors
            +errors() List~String~
        }
    }

    namespace collection {
        class CustomArrayList~T~ {
            -Object[] elements
            -int size
            +add(T item) boolean
            +get(int index) T
            +set(int index, T item) T
            +remove(int index) T
            +size() int
            +isEmpty() boolean
            +iterator() Iterator~T~
            +stream() Stream~T~
        }
        class CustomCollectors {
            <<utility>>
            +toCustomList() Collector$
        }
    }

    namespace data {
        class DataProvider~T~ {
            <<functional interface>>
            +provide(int size) List~T~
        }
        class RandomBusProvider {
            -Supplier~Bus~ randomBusFactory
            +provide(int size) List~Bus~
        }
        class ManualBusProvider {
            +provide(int size) List~Bus~
        }
        class FileBusProvider {
            -Path source
            +provide(int size) List~Bus~
        }
        class BusLineParser {
            -String SEPARATOR
            +parse(String line) Parsed~Bus~
        }
        class Parsed~T~ {
            <<immutable>>
            +isValid() boolean
            +value() Optional~T~
            +errors() List~String~
            +ok(T value) Parsed~T~$
            +fail(List~String~ errors) Parsed~T~$
        }
        class FillMode {
            <<enumeration>>
            RANDOM
            MANUAL
            FILE
            +provider(UserInputReader reader) DataProvider~Bus~
        }
    }

    %% === СВЯЗИ (вынесены за пределы namespace) ===

    %% Domain
    Bus ..> BusBuilder : создаётся через build()

    %% Validation
    Validator ..> ValidationResult : возвращает
    BusValidators ..> Validator : поставляет правила
    InvalidDataException ..> ValidationResult : несёт ошибки

    %% Collection
    CustomCollectors ..> CustomArrayList : собирает стримы в

    %% Data
    DataProvider <|.. RandomBusProvider
    DataProvider <|.. ManualBusProvider
    DataProvider <|.. FileBusProvider
    FillMode ..> DataProvider : выбирает реализацию
    ManualBusProvider ..> BusLineParser
    FileBusProvider ..> BusLineParser
    BusLineParser ..> Parsed
    BusLineParser ..> BusBuilder

    %% Cross-namespace
    BusBuilder ..> BusValidators : применяет в build()
    BusBuilder ..> InvalidDataException : выбрасывает при ошибках
    RandomBusProvider ..> Bus : строит через BusBuilder
    RandomBusProvider ..> CustomCollectors
    ManualBusProvider ..> CustomCollectors
    FileBusProvider ..> CustomCollectors
```

### 2.3. Диаграмма классов — алгоритмы, инфраструктура, интерфейс

```mermaid
classDiagram
    direction TB

    namespace algorithm {
        class SortStrategy~T~ {
            <<functional interface>>
            +sort(List~T~ source) List~T~
        }
        class TimSort {
            <<utility>>
            +sort(List~T~ source, Comparator~T~ order) List~T~$
            -minRunLength(int n) int$
            -countRunAndMakeAscending(a, lo, hi, cmp) int$
            -binaryInsertionSort(a, lo, hi, cmp) void$
            -pushRun(base, len) void$
            -mergeCollapse() void$
            -mergeAt(int i) void$
            -mergeLo(base1, len1, base2, len2) void$
            -mergeHi(base1, len1, base2, len2) void$
            -gallopLeft(key, a, base, len, hint) int$
            -gallopRight(key, a, base, len, hint) int$
        }
        class ComparatorBuilder {
            <<utility>>
            +comparing(Function key) Comparator$
            +reversed(Comparator order) Comparator$
            +thenComparing(Comparator first, Comparator second) Comparator$
        }
        class BusComparators {
            <<utility>>
            +byRouteNumber() Comparator~Bus~$
            +byModel() Comparator~Bus~$
            +byMileage() Comparator~Bus~$
        }
        class SortKey {
            <<enumeration>>
            ROUTE_NUMBER
            MODEL
            MILEAGE
            +comparator() Comparator~Bus~
        }
        class SortDirection {
            <<enumeration>>
            ASCENDING
            DESCENDING
            +apply(Comparator order) Comparator
        }
        class SortMode {
            <<enumeration>>
            FULL
            EVEN_ONLY
        }
        class ParitySortStrategy {
            -ToIntFunction~Bus~ keyExtractor
            +sort(List~Bus~ source) List~Bus~
        }
        class SortingService {
            +sort(List~Bus~ source, SortKey key, SortDirection dir, SortMode mode) List~Bus~
            -strategyFor(SortKey, SortDirection, SortMode) SortStrategy~Bus~
        }
    }

    namespace io {
        class UserInputReader {
            -Scanner scanner
            +readLine() String
            +readInt(String prompt, int min, int max) int
            +readLong(String prompt) long
            +readExistingFilePath(String prompt) Path
        }
        class ResultWriter {
            +appendAll(Path target, List~Bus~ items, Function formatter) void
        }
        class BusFormatter {
            <<utility>>
            +header() String$
            +toCsv(Bus bus) String$
        }
    }

    namespace concurrent {
        class OccurrenceCounter {
            +countOccurrences(List~Bus~ items, Bus target, int threads) long
            -partition(List~Bus~ items, int parts) List
            -countIn(List~Bus~ chunk, Bus target) long
        }
    }

    namespace ui {
        class Main {
            +main(String[] args) void$
        }
        class ApplicationRunner {
            -UserInputReader reader
            -ConsoleMenu menu
            -List~Bus~ sourceItems
            -List~Bus~ sortedItems
            -boolean running
            +run() void
            -onFill() void
            -onSort() void
            -onShow() void
            -onSave() void
            -onCount() void
            -onExit() void
        }
        class ConsoleMenu {
            -Map actions
            +render() void
            +dispatch(int choice) void
            -registerDefaults() void
        }
        class MenuAction {
            <<functional interface>>
            +run() void
        }
    }

    %% === СВЯЗИ (вынесены за пределы namespace) ===

    %% Algorithm
    SortStrategy <|.. ParitySortStrategy
    SortingService ..> SortStrategy : контекст стратегии
    SortingService ..> SortKey
    SortingService ..> SortDirection
    SortingService ..> SortMode
    SortKey ..> BusComparators
    BusComparators ..> ComparatorBuilder
    ParitySortStrategy ..> TimSort
    SortingService ..> TimSort

    %% IO
    ResultWriter ..> BusFormatter : использует форматтер

    %% UI
    Main ..> ApplicationRunner : запускает
    ApplicationRunner ..> ConsoleMenu
    ConsoleMenu ..> MenuAction : реестр лямбда-команд
    ApplicationRunner ..> UserInputReader
    ApplicationRunner ..> SortingService
    ApplicationRunner ..> ResultWriter
    ApplicationRunner ..> OccurrenceCounter
    ApplicationRunner ..> FillMode
```

### 2.4. Диаграмма состояний — жизненный цикл приложения

```mermaid
stateDiagram-v2
    [*] --> MainMenu : запуск приложения

    MainMenu --> FillMenu : 1. Заполнить коллекцию
    MainMenu --> SortMenu : 2. Сортировать [коллекция задана]
    MainMenu --> ShowResult : 3. Показать коллекцию [коллекция задана]
    MainMenu --> SaveResult : 4. Записать в файл [есть результат]
    MainMenu --> CountMenu : 5. Подсчитать вхождения [коллекция задана]
    MainMenu --> Terminated : 0. Выход
    MainMenu --> MainMenu : некорректный выбор / guard не выполнен

    FillMenu --> SourceSelect : выбор источника
    SourceSelect --> RandomFill : «случайные значения»
    SourceSelect --> ManualFill : «вручную»
    SourceSelect --> FileFill : «из файла»
    RandomFill --> MainMenu : коллекция создана стримом
    ManualFill --> MainMenu : коллекция создана с валидацией ввода
    FileFill --> MainMenu : валидные строки прочитаны

    SortMenu --> KeySelect : выбор поля (номер / модель / пробег)
    KeySelect --> DirectionSelect : по возрастанию / по убыванию
    DirectionSelect --> ModeSelect : полная / только чётные
    ModeSelect --> MainMenu : результат готов

    ShowResult --> MainMenu
    SaveResult --> MainMenu : файл дописан в режиме append
    CountMenu --> MainMenu : результат выведен в консоль

    Terminated --> [*] : флаг running снят
```

### 2.5. Диаграмма последовательностей — основной сценарий

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Runner as ApplicationRunner
    participant Menu as ConsoleMenu
    participant File as FileBusProvider
    participant Parser as BusLineParser
    participant Builder as BusBuilder
    participant Service as SortingService
    participant Engine as TimSort
    participant Writer as ResultWriter

    User->>Runner: запуск приложения
    loop пока не выбран пункт Выход
        Runner->>Menu: render()
        Menu-->>User: пункты главного меню
        User->>Runner: 1 - заполнить из файла
        Runner->>File: provide(size)
        File->>Parser: parse(42, ЛиАЗ-5256, 250000)
        Parser->>Builder: routeNumber(42), model(ЛиАЗ-5256), mileage(250000), build()
        Builder->>Builder: валидатор forBuilder - все правила
        alt все правила пройдены
            Builder-->>Parser: Bus(42, ЛиАЗ-5256, 250000)
            Parser-->>File: Parsed.ok(bus)
        else есть нарушения
            Builder-->>Parser: InvalidDataException(errors)
            Parser-->>File: Parsed.fail(errors)
            File-->>User: предупреждение - строка пропущена
        end
        File-->>Runner: исходная коллекция CustomArrayList
        User->>Runner: 2 - сортировать MODEL, ASCENDING, FULL
        Runner->>Service: sort(source, MODEL, ASCENDING, FULL)
        Service->>Service: strategyFor - композиция функций
        Service->>Engine: sort(копия, byModel)
        Engine-->>Service: новая отсортированная коллекция
        Service-->>Runner: результат
        Runner-->>User: вывод коллекции в консоль
        User->>Runner: 4 - сохранить в файл
        Runner->>Writer: appendAll(path, result, BusFormatter toCsv)
        Writer->>Writer: Files.write(CREATE + APPEND)
        Writer-->>Runner: успешно
        Runner-->>User: результат дописан в файл
    end
    User->>Runner: 0 - выход
    Runner-->>User: завершение работы
```

### 2.6. Диаграмма последовательностей — многопоточный подсчёт вхождений

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Runner as ApplicationRunner
    participant Reader as UserInputReader
    participant Counter as OccurrenceCounter
    participant Pool as ExecutorService
    participant W1 as Worker 1
    participant W2 as Worker 2
    participant WN as Worker N

    User->>Runner: 5 - подсчитать вхождения
    Runner->>Reader: readInt(число потоков)
    User-->>Reader: N
    Runner->>Reader: ввод искомого элемента номер, модель, пробег
    User-->>Reader: 42, ЛиАЗ-5256, 250000
    Reader-->>Runner: Bus target (валидный)
    Runner->>Counter: countOccurrences(source, target, N)
    Counter->>Counter: partition(source, N) - разбиение на сегменты
    par параллельные задачи
        Counter->>Pool: submit(chunk-1)
        Pool->>W1: call()
        W1->>W1: подсчёт совпадений по equals
        W1-->>Pool: локальная сумма c1
    and
        Counter->>Pool: submit(chunk-2)
        Pool->>W2: call()
        W2->>W2: подсчёт совпадений по equals
        W2-->>Pool: локальная сумма c2
    and
        Counter->>Pool: submit(chunk-N)
        Pool->>WN: call()
        WN-->>Pool: локальная сумма cN
    end
    Counter->>Counter: total = c1 + c2 + ... + cN (редукция)
    Counter-->>Runner: total
    Runner-->>User: Элемент встречается total раз(а)
```

### 2.7. Диаграмма деятельности — алгоритм Timsort

```mermaid
flowchart TD
    Start(["sort(список, компаратор) — вход не изменяется"]) --> Copy["Скопировать элементы в рабочий массив"]
    Copy --> MinRun["Вычислить minRun: значение 32–64,<br/>дающее n по модулю близкое к степени двойки"]
    MinRun --> Loop{"Остались<br/>нераспределённые элементы?"}
    Loop -- "да" --> Run["Выделить очередной run;<br/>строго убывающий — развернуть"]
    Run --> Short{"len(run) &lt; minRun?"}
    Short -- "да" --> Insert["Дополнить run до minRun<br/>бинарной сортировкой вставкой"]
    Short -- "нет" --> Push["Поместить run в стек серий"]
    Insert --> Push
    Push --> Collapse{"Нарушены инварианты стека?<br/>runLen[i-3] &gt; runLen[i-2] + runLen[i-1]<br/>или runLen[i-2] &gt; runLen[i-1]"}
    Collapse -- "да" --> Merge["Слить вершины стека<br/>mergeLo / mergeHi + галопирование<br/>gallopLeft / gallopRight"]
    Merge --> Collapse
    Collapse -- "нет" --> Loop
    Loop -- "нет" --> Final["Слить все оставшиеся run'ы<br/>до единственного отсортированного"]
    Final --> Result(["Вернуть новый CustomArrayList"])
```

### 2.8. Диаграмма деятельности — стратегия «чётные по натуральному порядку»

```mermaid
flowchart TD
    Start(["sortEvenOnly(список, числовой ключ = номер маршрута)"]) --> Idx["IntStream по индексам:<br/>собрать позиции, где ключ элемента чётный"]
    Idx --> Extract["Извлечь подсписок элементов с чётным ключом<br/>с сохранением исходного порядка"]
    Extract --> Sort["TimSort подсписка<br/>по натуральному порядку ключа (возрастание)"]
    Sort --> Place["Разложить отсортированные элементы<br/>обратно по сохранённым индексам"]
    Place --> Note["Элементы с нечётным ключом<br/>остаются на исходных позициях"]
    Note --> Result(["Готовая коллекция — новая копия"])
```

---

Описанная архитектура закрывает все пункты задания: собственный Timsort с компараторами по трём полям, паттерны Builder и Strategy, три способа валидируемого заполнения (стримами, в кастомную коллекцию), частичная сортировка чётных, дозапись результатов в файл и многопоточный подсчёт вхождений — при этом функциональный стиль проведён последовательно во всех чистых слоях и сознательно ограничен там, где задание требует императивных конструкций (главный цикл, консольный ввод-вывод, пул потоков).