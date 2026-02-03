# Архитектура

## Слои

- `bluetooth/` — BLE транспорт и очередь GATT операций
- `data/` — модели, парсинг protobuf, репозиторий
- `ui/` — фрагменты экрана и адаптеры

## Поток данных (BLE)

```
BLE (ToRadio/FromRadio)
  -> BleManager
  -> MeshConnectionRepository
  -> LiveData
  -> UI (Fragments)
```

## Ключевые классы

- `BleManager` — сканирование и GATT операции, drain FromRadio
- `MeshConnectionRepository` — единая точка состояния и отправки команд
- `MeshProtoParser` — человекочитаемые сводки FromRadio
- `StatusFragment` — визуальная обратная связь
- `SettingsFragment` — черновик и применение PSK

