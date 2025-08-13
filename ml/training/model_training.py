#!/usr/bin/env python3
"""
LogisticRegression 중요도 모델 학습
TICKET-102: LogisticRegression 중요도 모델 학습 (PR-AUC ≥ 0.70)
"""

import os
import numpy as np
import pandas as pd
import json
import pickle
from datetime import datetime
from typing import Dict, Tuple, Any
import logging

from sklearn.model_selection import cross_val_score, StratifiedKFold, GridSearchCV
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    precision_recall_curve, auc, f1_score, precision_score, 
    recall_score, classification_report, confusion_matrix
)
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import StandardScaler
import matplotlib.pyplot as plt
import seaborn as sns

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class ImportanceModelTrainer:
    def __init__(self, data_dir: str = "data"):
        self.data_dir = data_dir
        self.model = None
        self.cv_results = {}
        self.feature_names = []
        
    def load_dataset(self) -> Tuple[np.ndarray, np.ndarray, pd.DataFrame]:
        """학습 데이터셋 로드"""
        logger.info("데이터셋 로딩 중...")
        
        # 피처와 라벨 로드
        features = np.load(f"{self.data_dir}/features.npy")
        labels = np.load(f"{self.data_dir}/labels.npy")
        
        # 원본 데이터프레임 로드 (메타데이터용)
        df = pd.read_csv(f"{self.data_dir}/news_dataset.csv")
        
        # 메타데이터 로드
        with open(f"{self.data_dir}/metadata.json") as f:
            metadata = json.load(f)
        
        logger.info(f"피처 shape: {features.shape}")
        logger.info(f"라벨 분포: {np.bincount(labels)}")
        logger.info(f"메타데이터: {metadata}")
        
        return features, labels, df
    
    def hyperparameter_tuning(self, X: np.ndarray, y: np.ndarray) -> Dict[str, Any]:
        """하이퍼파라미터 튜닝"""
        logger.info("하이퍼파라미터 튜닝 시작...")
        
        # 파라미터 그리드 정의
        param_grid = {
            'C': [0.01, 0.1, 1.0, 10.0, 100.0],
            'max_iter': [1000, 2000, 5000],
            'class_weight': [None, 'balanced'],
            'solver': ['saga', 'liblinear']
        }
        
        # 기본 모델
        base_model = LogisticRegression(random_state=42, n_jobs=-1)
        
        # GridSearchCV
        cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
        grid_search = GridSearchCV(
            base_model, 
            param_grid, 
            cv=cv,
            scoring='average_precision',  # PR-AUC
            n_jobs=-1,
            verbose=1
        )
        
        grid_search.fit(X, y)
        
        logger.info(f"최적 파라미터: {grid_search.best_params_}")
        logger.info(f"최적 PR-AUC: {grid_search.best_score_:.4f}")
        
        return {
            'best_params': grid_search.best_params_,
            'best_score': grid_search.best_score_,
            'cv_results': grid_search.cv_results_
        }
    
    def train_final_model(self, X: np.ndarray, y: np.ndarray, 
                         best_params: Dict[str, Any]) -> LogisticRegression:
        """최종 모델 학습"""
        logger.info("최종 모델 학습 중...")
        
        # 최적 파라미터로 모델 생성
        model = LogisticRegression(
            random_state=42,
            n_jobs=-1,
            **best_params
        )
        
        # 전체 데이터로 학습
        model.fit(X, y)
        
        self.model = model
        logger.info("모델 학습 완료")
        
        return model
    
    def cross_validation_evaluation(self, X: np.ndarray, y: np.ndarray) -> Dict[str, float]:
        """5-fold Cross Validation 평가"""
        logger.info("5-fold Cross Validation 평가 중...")
        
        cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
        
        # 다양한 메트릭으로 CV 평가
        scoring_metrics = ['precision', 'recall', 'f1', 'average_precision']
        cv_scores = {}
        
        for metric in scoring_metrics:
            scores = cross_val_score(self.model, X, y, cv=cv, scoring=metric)
            cv_scores[f'{metric}_mean'] = scores.mean()
            cv_scores[f'{metric}_std'] = scores.std()
            
            logger.info(f"{metric}: {scores.mean():.4f} (+/- {scores.std() * 2:.4f})")
        
        self.cv_results = cv_scores
        return cv_scores
    
    def detailed_evaluation(self, X: np.ndarray, y: np.ndarray) -> Dict[str, Any]:
        """상세 모델 평가"""
        logger.info("상세 평가 중...")
        
        # 예측
        y_pred = self.model.predict(X)
        y_proba = self.model.predict_proba(X)[:, 1]
        
        # PR 곡선 계산
        precision, recall, _ = precision_recall_curve(y, y_proba)
        pr_auc = auc(recall, precision)
        
        # 기본 메트릭
        f1 = f1_score(y, y_pred)
        precision_score_val = precision_score(y, y_pred)
        recall_score_val = recall_score(y, y_pred)
        
        # 분류 리포트
        classification_rep = classification_report(y, y_pred, target_names=['Low', 'High'])
        
        # 혼동 행렬
        cm = confusion_matrix(y, y_pred)
        
        results = {
            'pr_auc': pr_auc,
            'f1_score': f1,
            'precision': precision_score_val,
            'recall': recall_score_val,
            'classification_report': classification_rep,
            'confusion_matrix': cm.tolist(),
            'precision_curve': precision.tolist(),
            'recall_curve': recall.tolist()
        }
        
        logger.info(f"PR-AUC: {pr_auc:.4f}")
        logger.info(f"F1-Score: {f1:.4f}")
        logger.info(f"Precision: {precision_score_val:.4f}")
        logger.info(f"Recall: {recall_score_val:.4f}")
        
        return results
    
    def feature_importance_analysis(self, feature_names: list = None) -> Dict[str, float]:
        """피처 중요도 분석"""
        logger.info("피처 중요도 분석 중...")
        
        if self.model is None:
            raise ValueError("모델이 학습되지 않았습니다.")
        
        # 로지스틱 회귀의 계수 (절댓값)
        coefficients = np.abs(self.model.coef_[0])
        
        # 피처 이름이 없으면 인덱스 사용
        if feature_names is None:
            feature_names = [f"feature_{i}" for i in range(len(coefficients))]
        
        # 중요도 정렬
        feature_importance = dict(zip(feature_names, coefficients))
        sorted_importance = dict(sorted(feature_importance.items(), 
                                      key=lambda x: x[1], reverse=True))
        
        # 상위 20개 피처 로깅
        logger.info("상위 20개 중요 피처:")
        for i, (feature, importance) in enumerate(list(sorted_importance.items())[:20]):
            logger.info(f"{i+1:2d}. {feature}: {importance:.4f}")
        
        return sorted_importance
    
    def save_model_artifacts(self, model: LogisticRegression, 
                           hyperparams: Dict[str, Any],
                           cv_results: Dict[str, float],
                           evaluation_results: Dict[str, Any],
                           feature_importance: Dict[str, float],
                           output_dir: str = "models") -> str:
        """모델 아티팩트 저장"""
        logger.info("모델 아티팩트 저장 중...")
        
        os.makedirs(output_dir, exist_ok=True)
        
        # 모델 버전 생성 (날짜 기반)
        model_version = datetime.now().strftime("v%Y%m%d_%H%M%S")
        model_filename = f"importance-{model_version}.pkl"
        model_path = os.path.join(output_dir, model_filename)
        
        # 모델 저장
        with open(model_path, 'wb') as f:
            pickle.dump(model, f)
        
        # 메타데이터 저장
        metadata = {
            "model_version": model_version,
            "created_at": datetime.now().isoformat(),
            "model_type": "LogisticRegression",
            "hyperparameters": hyperparams,
            "cv_results": cv_results,
            "evaluation_results": {
                k: v for k, v in evaluation_results.items() 
                if k not in ['precision_curve', 'recall_curve']  # 너무 큰 데이터 제외
            },
            "feature_importance_top20": dict(list(feature_importance.items())[:20]),
            "training_samples": len(cv_results),
            "meets_acceptance_criteria": {
                "pr_auc_ge_070": evaluation_results['pr_auc'] >= 0.70,
                "f1_score_ge_065": evaluation_results['f1_score'] >= 0.65,
                "overall_pass": (evaluation_results['pr_auc'] >= 0.70 and 
                               evaluation_results['f1_score'] >= 0.65)
            }
        }
        
        metadata_path = os.path.join(output_dir, f"metadata-{model_version}.json")
        with open(metadata_path, 'w') as f:
            json.dump(metadata, f, indent=2, ensure_ascii=False)
        
        # 최신 모델 심볼릭 링크 생성
        latest_model_path = os.path.join(output_dir, "importance-latest.pkl")
        latest_metadata_path = os.path.join(output_dir, "metadata-latest.json")
        
        # 기존 링크 제거 후 새로 생성
        for path in [latest_model_path, latest_metadata_path]:
            if os.path.exists(path):
                os.remove(path)
        
        os.symlink(model_filename, latest_model_path)
        os.symlink(f"metadata-{model_version}.json", latest_metadata_path)
        
        logger.info(f"모델 저장 완료: {model_path}")
        logger.info(f"메타데이터 저장 완료: {metadata_path}")
        
        return model_version
    
    def create_evaluation_plots(self, evaluation_results: Dict[str, Any], 
                              output_dir: str = "models") -> None:
        """평가 결과 시각화"""
        logger.info("평가 플롯 생성 중...")
        
        # 혼동 행렬 플롯
        plt.figure(figsize=(10, 8))
        
        plt.subplot(2, 2, 1)
        cm = np.array(evaluation_results['confusion_matrix'])
        sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                   xticklabels=['Low', 'High'], yticklabels=['Low', 'High'])
        plt.title('Confusion Matrix')
        plt.xlabel('Predicted')
        plt.ylabel('Actual')
        
        # PR 곡선 플롯
        plt.subplot(2, 2, 2)
        precision = evaluation_results['precision_curve']
        recall = evaluation_results['recall_curve']
        pr_auc = evaluation_results['pr_auc']
        
        plt.plot(recall, precision, label=f'PR Curve (AUC = {pr_auc:.3f})')
        plt.xlabel('Recall')
        plt.ylabel('Precision')
        plt.title('Precision-Recall Curve')
        plt.legend()
        plt.grid(True)
        
        # 메트릭 바 차트
        plt.subplot(2, 2, 3)
        metrics = ['PR-AUC', 'F1-Score', 'Precision', 'Recall']
        values = [
            evaluation_results['pr_auc'],
            evaluation_results['f1_score'],
            evaluation_results['precision'],
            evaluation_results['recall']
        ]
        
        bars = plt.bar(metrics, values, color=['blue', 'green', 'orange', 'red'])
        plt.title('Model Performance Metrics')
        plt.ylim(0, 1)
        
        # 수락 기준 표시
        plt.axhline(y=0.70, color='red', linestyle='--', alpha=0.7, label='PR-AUC Target (0.70)')
        plt.axhline(y=0.65, color='green', linestyle='--', alpha=0.7, label='F1 Target (0.65)')
        
        for bar, value in zip(bars, values):
            plt.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.01,
                    f'{value:.3f}', ha='center', va='bottom')
        
        plt.xticks(rotation=45)
        plt.legend()
        
        plt.tight_layout()
        
        # 플롯 저장
        plot_path = os.path.join(output_dir, f"evaluation_plots_{datetime.now().strftime('%Y%m%d_%H%M%S')}.png")
        plt.savefig(plot_path, dpi=300, bbox_inches='tight')
        logger.info(f"평가 플롯 저장: {plot_path}")
        
        plt.show()

def main():
    """메인 실행 함수"""
    logger.info("=== 중요도 모델 학습 시작 ===")
    
    # 트레이너 초기화
    trainer = ImportanceModelTrainer()
    
    # 1. 데이터셋 로드
    X, y, df = trainer.load_dataset()
    
    # 2. 하이퍼파라미터 튜닝
    tuning_results = trainer.hyperparameter_tuning(X, y)
    
    # 3. 최종 모델 학습
    model = trainer.train_final_model(X, y, tuning_results['best_params'])
    
    # 4. Cross Validation 평가
    cv_results = trainer.cross_validation_evaluation(X, y)
    
    # 5. 상세 평가
    evaluation_results = trainer.detailed_evaluation(X, y)
    
    # 6. 피처 중요도 분석
    feature_importance = trainer.feature_importance_analysis()
    
    # 7. 모델 아티팩트 저장
    model_version = trainer.save_model_artifacts(
        model, tuning_results['best_params'], cv_results, 
        evaluation_results, feature_importance
    )
    
    # 8. 평가 플롯 생성
    trainer.create_evaluation_plots(evaluation_results)
    
    # 9. 수락 기준 체크
    pr_auc = evaluation_results['pr_auc']
    f1_score_val = evaluation_results['f1_score']
    
    logger.info("=== 모델 학습 완료 ===")
    logger.info(f"모델 버전: {model_version}")
    logger.info(f"PR-AUC: {pr_auc:.4f} (목표: ≥ 0.70)")
    logger.info(f"F1-Score: {f1_score_val:.4f} (목표: ≥ 0.65)")
    
    if pr_auc >= 0.70 and f1_score_val >= 0.65:
        logger.info("✅ 수락 기준 달성!")
    else:
        logger.warning("❌ 수락 기준 미달성")
        
    return {
        'model_version': model_version,
        'pr_auc': pr_auc,
        'f1_score': f1_score_val,
        'acceptance_criteria_met': pr_auc >= 0.70 and f1_score_val >= 0.65
    }

if __name__ == "__main__":
    results = main()